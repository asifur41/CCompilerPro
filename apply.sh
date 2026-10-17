#!/usr/bin/env bash
# Run from the repo root after unzipping toolchain-patch.zip
set -e
F=app/src/main/java/com/ccompiler/pro/MainActivity.kt
python3 - <<'PY'
p='app/src/main/java/com/ccompiler/pro/MainActivity.kt'
s=open(p).read()
a='val exe = File(dir, "prog")'
b='val r = executor.run(exe, stdin, dir) { s, isErr ->'
if 'prog.o' in s:
    print('MainActivity already patched')
else:
    assert a in s and b in s, 'MainActivity.kt does not look like the expected version'
    s=s.replace(a,'val exe = File(dir, "prog.o")').replace(b,'val r = executor.run(compiler.runCommand(src), stdin, dir) { s, isErr ->')
    open(p,'w').write(s)
    print('MainActivity patched')
PY
chmod +x ci.sh scripts/build_tcc.sh scripts/fetch_fonts.sh gradlew 2>/dev/null || true
rm -f toolchain-patch.zip
echo "Patch applied. Now run:  ./ci.sh \"add tcc toolchain\""
