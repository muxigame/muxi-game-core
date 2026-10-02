from pathlib import Path
import subprocess, zipfile
root=Path(__file__).resolve().parent/'normal-close-agent'
result=subprocess.run([r'C:\Program Files\Java\jdk-24\bin\javac.exe','--release','21','--add-modules','jdk.attach','-d',str(root),str(root/'Task23NormalCloseAgent.java'),str(root/'Task23Attach.java')],capture_output=True,timeout=30)
if result.returncode:print(result.stderr.decode(errors='replace'));raise SystemExit(result.returncode)
with zipfile.ZipFile(root/'task23-normal-close-agent.jar','w') as z:
 z.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nAgent-Class: Task23NormalCloseAgent\n\n')
 z.write(root/'Task23NormalCloseAgent.class','Task23NormalCloseAgent.class')
print('Compiled temporary agent and attach helper; only verified PID33816 standard server stop.')
