#!/usr/bin/env python3
"""Create a reviewable Windows source-patch delivery without committing/pushing."""
from pathlib import Path
import hashlib,json,subprocess,shutil,zipfile,runpy,tempfile
HERE=Path(__file__).resolve().parent
REPO=HERE.parents[3]
BASELINE='5c58ea10e288cb6e3e67079cfbc3d1332b6f9bab'
OUT=REPO/'build'/'warehouse-artifacts'
NAME='Ellie_Storage_Warehouse_Apply_Build_Verify_20261112'
PACKAGE=OUT/'package'
OUT.mkdir(parents=True,exist_ok=True)
if PACKAGE.exists():
    if PACKAGE.resolve()!=OUT.resolve()/'package':
        raise ValueError(f'Unexpected packaging directory: {PACKAGE}')
    shutil.rmtree(PACKAGE)
PACKAGE.mkdir()
def git(*args):return subprocess.check_output(['git','-C',str(REPO),*args])
changed=git('diff','--name-only',BASELINE).decode().splitlines()
untracked=git('ls-files','--others','--exclude-standard').decode().splitlines()
paths=sorted({p for p in changed+untracked if p.startswith('ellie/fabric-1.21.1-recovered/') and Path(p).suffix in {'.java','.py','.ps1','.cmd','.md','.html','.json','.gradle'}})
manifest={'schema':1,'baseline':BASELINE,'branch':'feature/ellie-waterfill-access-and-reach-20261111','files':[]}
for relative in paths:
    source=REPO/relative;target=PACKAGE/'payload'/relative
    target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
    original=subprocess.run(['git','-C',str(REPO),'show',f'{BASELINE}:{relative}'],capture_output=True)
    manifest['files'].append({'path':relative,'before':hashlib.sha256(original.stdout).hexdigest() if original.returncode==0 else None,'after':hashlib.sha256(source.read_bytes()).hexdigest()})
(PACKAGE/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
patch=git('diff','--binary',BASELINE,'--','ellie/fabric-1.21.1-recovered')
for relative in sorted(set(untracked)&set(paths)):
    result=subprocess.run(['git','diff','--no-index','--binary','--','/dev/null',relative],cwd=REPO,capture_output=True)
    if result.returncode not in (0,1):raise RuntimeError(result.stderr.decode())
    patch+=result.stdout
(PACKAGE/'warehouse.patch').write_bytes(patch)
for path in HERE.iterdir():
    if path.suffix in {'.cmd','.ps1'}:shutil.copyfile(path,PACKAGE/path.name)
fixture=runpy.run_path(str(HERE.parent/'warehouse-tests/run.py'),run_name='warehouse_fixtures')
for rel,content in fixture['STUBS'].items():
    p=PACKAGE/'fixtures'/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
p=PACKAGE/'fixtures/warehousefixture/WarehouseTests.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(fixture['HARNESS'])
shutil.copyfile(HERE.parents[1]/'WAREHOUSE.md',PACKAGE/'README.md')
reports=PACKAGE/'reports';reports.mkdir()
for name,filename in [('fabric-build.log','ellie-warehouse-build.log'),('logic-tests.log','ellie-warehouse-tests.log'),('delivery-tests.log','ellie-delivery-tests.log')]:
    path=Path(tempfile.gettempdir())/filename
    if path.exists():shutil.copyfile(path,reports/name)
if (OUT/'WAREHOUSE_VALIDATION.md').exists():shutil.copyfile(OUT/'WAREHOUSE_VALIDATION.md',PACKAGE/'VALIDATION.md')
jars=sorted((REPO/'ellie/fabric-1.21.1-recovered/fabric/build/libs').glob('*.jar'))
for jar in jars:
    if any(s in jar.name for s in ('sources','dev','shadow','javadoc')):continue
    binary=PACKAGE/'candidate';binary.mkdir(exist_ok=True)
    shutil.copyfile(jar,binary/'Ellie_Storage_Warehouse_1.21.1_CANDIDATE.jar')
checks={str(p.relative_to(PACKAGE)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in PACKAGE.rglob('*') if p.is_file()}
(PACKAGE/'FILES_SHA256.json').write_text(json.dumps(checks,indent=2)+'\n')
archive=OUT/(NAME+'.zip')
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(PACKAGE.rglob('*')):
        if p.is_file():z.write(p,str(p.relative_to(PACKAGE)).replace('\\','/'))
print(json.dumps({'archive':str(archive),'bytes':archive.stat().st_size,'patched_files':len(paths),'sha256':hashlib.sha256(archive.read_bytes()).hexdigest()}))
