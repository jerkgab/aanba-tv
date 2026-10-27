#!/usr/bin/env python3
"""Check the publication allowlist and common secret/private-device patterns. No secrets printed."""
from pathlib import Path
import argparse, json, re, zipfile, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
SKIP={'.git','.gradle','build','__pycache__'}
RULES={
 'absolute_home_path':re.compile(rb'(?:/Users/|/home/[^/\s]+/|/var/folders/)'),
 'private_device_address':re.compile(rb'\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3})\b'),
 'private_key':re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
 'github_token':re.compile(rb'(?:github_pat_[A-Za-z0-9_]{30,}|gh[pousr]_[A-Za-z0-9_]{25,})'),
 'embedded_credential':re.compile(rb'(?i)(?:api[_-]?key|access[_-]?token|client[_-]?secret)\s*[=:]\s*["\x27][A-Za-z0-9_-]{16,}'),
}
BAD_NAMES={'local.properties','.env','adbkey','adbkey.pub','cookies.txt','cookies.sqlite','verification'}
BAD_EXTS={'.jks','.keystore','.pem','.p12','.pfx'}
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--apk',type=Path);args=parser.parse_args();findings=[];count=0
 for path in ROOT.rglob('*'):
  rel=path.relative_to(ROOT)
  if any(p in SKIP for p in rel.parts):continue
  if any(p in BAD_NAMES for p in rel.parts) or path.suffix in BAD_EXTS:findings.append({'file':str(rel),'rule':'private_file'});continue
  if not path.is_file() or path.resolve()==Path(__file__).resolve():continue
  count+=1
  if path.suffix in {'.apk','.png','.jpg','.mp4','.jar','.zip'}:continue
  data=path.read_bytes()
  for rule,pattern in RULES.items():
   if pattern.search(data):findings.append({'file':str(rel),'rule':rule})
 manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot();android='{http://schemas.android.com/apk/res/android}'
 permissions=sorted(e.attrib[android+'name'] for e in manifest.findall('uses-permission'))
 if permissions!=['android.permission.ACCESS_NETWORK_STATE','android.permission.INTERNET']:findings.append({'rule':'unexpected_permissions'})
 if args.apk:
  with zipfile.ZipFile(args.apk) as z:
   for name in z.namelist():
    if name.endswith(('.dex','.js','.html','.json')):
     data=z.read(name)
     for rule,pattern in RULES.items():
      if pattern.search(data):findings.append({'file':'APK:'+name,'rule':rule})
    if name.endswith(('.jks','.keystore','.pem')):findings.append({'file':'APK:'+name,'rule':'private_file'})
 print(json.dumps({'files_checked':count,'manifest_permissions':permissions,'findings':findings},ensure_ascii=False,indent=2))
 raise SystemExit(bool(findings))
if __name__=='__main__':main()
