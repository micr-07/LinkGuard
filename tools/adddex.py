import shutil
import sys
import zipfile

# 把 classes.dex 追加进 APK，不重新压缩已有条目。
# 这样才能保证 resources.arsc 仍然是 STORED（targetSdk >= 30 的硬性要求）。

apk = sys.argv[1]
dex = sys.argv[2]
entry = sys.argv[3] if len(sys.argv) > 3 else "classes.dex"

with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    if entry in z.namelist():
        raise SystemExit("entry already present: " + entry)
    z.write(dex, entry)

with zipfile.ZipFile(apk) as z:
    print("appended %s" % entry)
    for n in z.namelist():
        if n.endswith("resources.arsc"):
            info = z.getinfo(n)
            print("resources.arsc compress_type=%d (0=STORED)" % info.compress_type)
