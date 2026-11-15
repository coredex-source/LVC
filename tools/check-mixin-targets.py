"""Static check of the Ornithe modules' mixin targets against each module's gen2 named Minecraft jar (javap).

Usage: python3 tools/check-mixin-targets.py .   (after the modules were configured once by Gradle)
Loom 1.19 remaps mixin strings in remapJar and does not validate them at compile time; this does.
Known false positive: generic parameters (javap prints T, the descriptor has the erasure)."""
import glob, json, pathlib, re, subprocess, sys, functools
root = pathlib.Path(sys.argv[1])
CACHE = root / ".gradle/loom-cache/minecraftMaven/net/minecraft"
PRIM = {"V": "void", "Z": "boolean", "B": "byte", "C": "char", "S": "short", "I": "int", "J": "long", "F": "float", "D": "double"}

def jar_for(version, side):
    kind = {"client": "client", "server": "server", "merged": "merged"}[side]
    c = [j for j in glob.glob(str(CACHE / f"minecraft-{kind}-*" / f"{version}-loom.mappings*" / "*.jar")) if "sources" not in j]
    return c[0] if c else None

@functools.lru_cache(None)
def javap(jar, cls):
    r = subprocess.run(["javap", "-p", "-cp", jar, cls], capture_output=True, text=True)
    return r.stdout if r.returncode == 0 else None

def parse_desc(desc):
    out, i = [], 0
    while i < len(desc):
        dims = 0
        while desc[i] == "[": dims += 1; i += 1
        if desc[i] == "L":
            j = desc.index(";", i); t = desc[i+1:j].replace("/", "."); i = j + 1
        else:
            t = PRIM[desc[i]]; i += 1
        out.append(t + "[]" * dims)
    return out

def member_ok(listing, name, desc=None, cls_name=None):
    if desc:
        args = desc[desc.index("(")+1:desc.index(")")]
        params = ", ".join(parse_desc(args)) if args else ""
        if name == "<init>":
            return f"{cls_name}({params})" in listing.replace("...", "[]")
        return f" {name}({params})" in listing.replace("...", "[]")
    return re.search(rf"[ .]{re.escape(name)}(\(|;|\s)", listing) is not None or (name in ("<init>", "<clinit>"))

problems = 0
modules = [p for p in sorted(root.glob("ornithe-*")) if (p / "gradle.properties").exists()] + \
          [p for p in sorted(root.glob("ornithe-*/*")) if (p / "gradle.properties").exists()]
for m in modules:
    props = dict(re.findall(r"^\s*([\w.]+)\s*=\s*(.*?)\s*$", (m / "gradle.properties").read_text(), re.M))
    # Every class a mixin config lists must exist (a missing one crashes the game at startup).
    for cfg in sorted(m.glob("src/main/resources/*.mixins.json")):
        conf = json.loads(cfg.read_text())
        pkg_dir = m / "src/main/java" / conf["package"].replace(".", "/")
        for name in (n for k in ("mixins", "client", "server") for n in conf.get(k, [])):
            if not (pkg_dir / (name.replace(".", "/") + ".java")).exists():
                print(f"MISSING MIXIN CLASS {cfg.relative_to(root)}: {name}"); problems += 1
    jar = jar_for(props["minecraft_version"], props["environment"])
    if not jar:
        print(f"{m}: no mapped jar (module not configured yet)"); continue
    for f in sorted(m.glob("src/main/java/**/mixin/*.java")):
        src = f.read_text()
        imports = dict((n.split(".")[-1], n) for n in re.findall(r"^import\s+([\w.]+);", src, re.M))
        mm = re.search(r"@Mixin\(\s*(?:value\s*=\s*)?\{?\s*(\w+)\.class", src)
        if not mm: continue
        tgt = imports.get(mm.group(1), mm.group(1))
        listing = javap(jar, tgt)
        rel = f.relative_to(root)
        if listing is None:
            print(f"MISSING CLASS {rel}: {tgt}"); problems += 1; continue
        simple = tgt.split(".")[-1]
        for meth in re.findall(r'method\s*=\s*\{?\s*"([^"]+)"', src):
            name = meth.split("(")[0]; desc = meth[len(name):] or None
            if not member_ok(listing, name, desc, tgt):
                print(f"MISSING METHOD {rel}: {tgt}.{meth}"); problems += 1
        for t in re.findall(r'target\s*=\s*"L([\w/$]+);([\w<>$]+)(\([^"]*)"', src):
            owner = t[0].replace("/", "."); ol = javap(jar, owner)
            if ol is None or not member_ok(ol, t[1], t[2], owner):
                print(f"MISSING TARGET {rel}: {owner}.{t[1]}{t[2]}"); problems += 1
        for acc in re.finditer(r'@(Accessor|Invoker)(?:\("([^"]+)"\))?\s*(?:public\s+|static\s+)*[\w<>\[\].]+\s+(\w+)\s*\(', src):
            kind, val, mname = acc.groups()
            name = val or (re.sub(r"^(get|set|is|call|invoke)", "", mname)[:1].lower() + re.sub(r"^(get|set|is|call|invoke)", "", mname)[1:])
            if not member_ok(listing, name):
                print(f"MISSING {kind.upper()} {rel}: {tgt}.{name}"); problems += 1
        for sh in re.findall(r"@Shadow\s+(?:@\w+\s+)*(?:(?:private|protected|public|static|final|abstract)\s+)*[\w<>\[\].]+\s+(\w+)\s*[;(=]", src):
            if not member_ok(listing, sh):
                print(f"MISSING SHADOW {rel}: {tgt}.{sh}"); problems += 1
        # A plain method of a mixin class with the name and arity of a target method replaces it silently
        # (LVC's KeyBindingMixin.isPressed() overwrote vanilla's from 1.8 on and crashed the client).
        if re.search(r"\bclass\s+\w+", src):
            for m in re.finditer(r"\n((?:[ \t]*@[^\n]*\n)*)[ \t]*(?:public|protected)\s+(?:static\s+|final\s+|synchronized\s+)*[\w<>\[\].]+\s+(\w+)\(([^)]*)\)\s*(?:throws [\w., ]+)?\{", src):
                annotations, name, params = m.group(1), m.group(2), m.group(3).strip()
                if re.search(r"@(Shadow|Overwrite|Inject|Redirect|Modify\w*|Accessor|Invoker|Unique|Intrinsic|Wrap\w*)\b", annotations) \
                        or "$" in name or name.startswith("lvc"):
                    continue
                arity = 0 if not params else params.count(",") + 1
                for line in listing.splitlines():
                    lm = re.search(rf"\s{re.escape(name)}\(([^)]*)\);", line)
                    if lm and (0 if not lm.group(1).strip() else lm.group(1).count(",") + 1) == arity:
                        print(f"IMPLICIT OVERWRITE {rel}: {name}({params}) replaces {tgt}.{name}"); problems += 1
                        break
print(f"{problems} problem(s)")
