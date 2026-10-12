from pathlib import Path
p=Path("apk-backup/app/src/main/java/com/personal/apkbackup/MainActivity.java")
s=p.read_text()
s=s.replace('outputName=a.packageName+(split?".apks":".apk");','outputName=a.packageName+".apk";')
s=s.replace('split?"application/zip":"application/vnd.android.package-archive"','"application/vnd.android.package-archive"')
s=s.replace('split?"This app uses split APKs. Export an .apks ZIP containing all installer APK parts. App data is NOT backed up.":"Export the installed APK file. App data and login details are NOT backed up."','split?"This app uses split APKs. APK-only mode saves the ORIGINAL base.apk without changing its signature, but it may NOT install or work without its required splits. This is not a complete backup.":"Export the installed APK file. App data and login details are NOT backed up."')
s=s.replace('''                    if(app.splitSourceDirs==null||app.splitSourceDirs.length==0){
                        copy(new FileInputStream(app.sourceDir),out);
                    }else{
                        try(ZipOutputStream zip=new ZipOutputStream(out)){
                            writeZip(zip,"base.apk",app.sourceDir);
                            for(int j=0;j<app.splitSourceDirs.length;j++)writeZip(zip,"split_"+j+".apk",app.splitSourceDirs[j]);
                            zip.finish();
                        }
                    }''','''                    copy(new FileInputStream(app.sourceDir),out);''')
start=s.index('                        if(app.splitSourceDirs==null||app.splitSourceDirs.length==0){')
end=s.index('                    });',start)
s=s[:start]+'''                        boolean split=app.splitSourceDirs!=null&&app.splitSourceDirs.length>0;
                        new AlertDialog.Builder(this).setTitle(split?"Base APK saved (may not install)":"Installable APK saved")
                          .setMessage(split?"This is the original base APK only. Additional split APKs may be required on another phone.":"Send this APK to another phone and open it using Android Installer.")
                          .setPositiveButton("SEND APK",(d,w)->shareBackup(uri,false))
                          .setNegativeButton("Done",null).show();
'''+s[end:]
s=s.replace('a.packageName+(split?".apks":".apk")','a.packageName+".apk"')
start=s.index('                if(split){',s.index('private void shareDirect('))
end=s.index('                Uri uri=FileProvider',start)
s=s[:start]+'''                copy(new FileInputStream(a.sourceDir),output);
'''+s[end:]
s=s.replace('if(split)Toast.makeText(this,"Split APK requires an installer on the other phone",Toast.LENGTH_LONG).show();','if(split)Toast.makeText(this,"Base APK only: installation may fail without required splits",Toast.LENGTH_LONG).show();')
s=s.replace('shareBackup(uri,split);','shareBackup(uri,false);')
s=s.replace('split?"SPLIT":"APK"','split?"BASE APK • SPLITS REQUIRED":"APK"')
p.write_text(s)
print("APK-only export configured with split compatibility warning")
