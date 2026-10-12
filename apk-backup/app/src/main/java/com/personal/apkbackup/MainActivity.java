package com.personal.apkbackup;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends Activity {
    private static final int SAVE=101, OPEN=102;
    private final ArrayList<ApplicationInfo> apps=new ArrayList<>();
    private LinearLayout list,root;
    private TextView status;
    private ApplicationInfo selected;
    private boolean showSystem=false;
    private String outputName;
    private final int BG=0xFF101318, PANEL=0xFF1B232E, ACCENT=0xFF19B7B0;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        root=new LinearLayout(this);root.setOrientation(1);root.setPadding(14,18,14,10);root.setBackgroundColor(BG);
        setContentView(root);
        label("Personal APK Backup",24,true);
        label("Offline • Installed app APK & split APK backup • Restore",12,false);
        LinearLayout tools=new LinearLayout(this);tools.setOrientation(0);
        Button restore=button("INSTALL BACKUP");tools.addView(restore,new LinearLayout.LayoutParams(0,52,1));
        Button toggle=button("SYSTEM APPS");tools.addView(toggle,new LinearLayout.LayoutParams(0,52,1));
        root.addView(tools);
        restore.setOnClickListener(v->openBackup());
        toggle.setOnClickListener(v->{showSystem=!showSystem;toggle.setText(showSystem?"USER APPS":"SYSTEM APPS");refresh();});
        status=label("Loading installed applications...",12,false);
        ScrollView sc=new ScrollView(this);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        list=new LinearLayout(this);list.setOrientation(1);sc.addView(list);
        refresh();
    }
    private TextView label(String s,int sp,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(sp);
        v.setPadding(8,8,8,8);if(bold)v.setTypeface(null,Typeface.BOLD);root.addView(v);return v;
    }
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));return b;}
    private void message(String s){status.setText(s);Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void refresh(){
        apps.clear();list.removeAllViews();PackageManager pm=getPackageManager();
        for(ApplicationInfo a:pm.getInstalledApplications(0)){
            if(!showSystem && (a.flags&ApplicationInfo.FLAG_SYSTEM)!=0)continue;
            if(a.sourceDir==null)continue;
            apps.add(a);
        }
        Collections.sort(apps,(a,b)->pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
        status.setText(apps.size()+" apps available. Tap one to export its installer.");
        for(ApplicationInfo a:apps){
            LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(10,9,10,9);
            row.setBackgroundColor(PANEL);
            String title=pm.getApplicationLabel(a).toString();
            TextView main=new TextView(this);main.setText(title+"  •  "+(a.splitSourceDirs!=null&&a.splitSourceDirs.length>0?"SPLIT":"APK"));
            main.setTextSize(16);main.setTypeface(null,Typeface.BOLD);main.setTextColor(Color.WHITE);row.addView(main);
            TextView sub=new TextView(this);sub.setText(a.packageName);sub.setTextSize(11);sub.setTextColor(0xFFB7C3D2);row.addView(sub);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=8;list.addView(row,lp);
            row.setOnClickListener(v->chooseExport(a));
        }
    }
    private void chooseExport(ApplicationInfo a){
        selected=a;
        boolean split=a.splitSourceDirs!=null&&a.splitSourceDirs.length>0;
        outputName=a.packageName+(split?".apks":".apk");
        new AlertDialog.Builder(this).setTitle(getPackageManager().getApplicationLabel(a))
          .setMessage(split?"This app uses split APKs. Export an .apks ZIP containing all installer APK parts. App data is NOT backed up.":"Export the installed APK file. App data and login details are NOT backed up.")
          .setPositiveButton("SAVE BACKUP",(d,w)->{
             Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
             i.addCategory(Intent.CATEGORY_OPENABLE);
             i.setType(split?"application/zip":"application/vnd.android.package-archive");
             i.putExtra(Intent.EXTRA_TITLE,outputName);
             startActivityForResult(i,SAVE);
          }).setNegativeButton("Cancel",null).show();
    }
    private void openBackup(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        startActivityForResult(i,OPEN);
    }
    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(req==SAVE&&selected!=null){
            ApplicationInfo app=selected;selected=null;
            message("Creating installer backup...");
            new Thread(()->{
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new IOException("Storage destination unavailable");
                    if(app.splitSourceDirs==null||app.splitSourceDirs.length==0){
                        copy(new FileInputStream(app.sourceDir),out);
                    }else{
                        try(ZipOutputStream zip=new ZipOutputStream(out)){
                            writeZip(zip,"base.apk",app.sourceDir);
                            for(int j=0;j<app.splitSourceDirs.length;j++)writeZip(zip,"split_"+j+".apk",app.splitSourceDirs[j]);
                            zip.finish();
                        }
                    }
                    runOnUiThread(()->message("Backup saved. Share the .apk/.apks file to your other phone."));
                }catch(Exception e){runOnUiThread(()->message("Backup failed: "+e.getMessage()));}
            }).start();
        }else if(req==OPEN){
            String name=uri.getLastPathSegment()==null?"":uri.getLastPathSegment().toLowerCase(Locale.ROOT);
            new AlertDialog.Builder(this).setTitle("Install backup")
                .setMessage("Only install backups you trust. Android may reject incompatible APKs or mismatched signatures. This restores the app, not its private data.")
                .setPositiveButton("INSTALL",(d,w)->startInstall(uri,name))
                .setNegativeButton("Cancel",null).show();
        }
    }
    private void writeZip(ZipOutputStream zip,String name,String source)throws IOException{
        zip.putNextEntry(new ZipEntry(name));
        copy(new FileInputStream(source),zip);zip.closeEntry();
    }
    private static void copy(InputStream from,OutputStream to)throws IOException{
        try(InputStream in=from){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)to.write(buf,0,n);}
    }
    private void startInstall(Uri uri,String name){
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
            message("Allow installs for this app in Settings, then tap INSTALL BACKUP again");
            Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()));
            startActivity(settings);return;
        }
        message("Preparing APK installation...");
        new Thread(()->{
            File temp=null;
            PackageInstaller.Session session=null;
            try{
                temp=File.createTempFile("installer_",".tmp",getCacheDir());
                try(OutputStream os=new FileOutputStream(temp)){copy(getContentResolver().openInputStream(uri),os);}
                ArrayList<File> packages=new ArrayList<>();
                boolean zip=false;
                try(ZipFile z=new ZipFile(temp)){
                    Enumeration<? extends ZipEntry> e=z.entries();
                    while(e.hasMoreElements()){
                        ZipEntry entry=e.nextElement();String en=entry.getName().toLowerCase(Locale.ROOT);
                        if(entry.isDirectory()||!en.endsWith(".apk")||en.contains("..")||en.startsWith("/"))continue;
                        File f=File.createTempFile("part_",".apk",getCacheDir());
                        try(InputStream in=z.getInputStream(entry);OutputStream out=new FileOutputStream(f)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
                        packages.add(f);
                    }
                    zip=true;
                }catch(ZipException notZip){zip=false;}
                if(!zip)packages.add(temp);
                if(packages.isEmpty())throw new IOException("No APK found in archive");
                long total=0;for(File f:packages)total+=f.length();
                PackageInstaller installer=getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setSize(total);
                int id=installer.createSession(params);
                session=installer.openSession(id);
                for(int j=0;j<packages.size();j++){
                    File f=packages.get(j);
                    try(InputStream in=new FileInputStream(f);OutputStream out=session.openWrite("part"+j+".apk",0,f.length())){
                        byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                        session.fsync(out);
                    }
                }
                Intent callback=new Intent(this,InstallReceiver.class).setAction("com.personal.apkbackup.INSTALL_STATUS");
                int flags=PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
                PendingIntent pending=PendingIntent.getBroadcast(this,id,callback,flags);
                session.commit(pending.getIntentSender());
                session.close();session=null;
                runOnUiThread(()->message("Android installer started. Confirm installation when prompted."));
                for(File f:packages)if(!f.equals(temp))f.delete();
            }catch(Exception e){
                if(session!=null){try{session.abandon();}catch(Exception ignored){}}
                String error=e.getMessage();runOnUiThread(()->message("Installation error: "+error));
            }finally{if(temp!=null)temp.delete();}
        }).start();
    }
    public static class InstallReceiver extends android.content.BroadcastReceiver{
        @Override public void onReceive(Context context,Intent intent){
            int code=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
            if(code==PackageInstaller.STATUS_PENDING_USER_ACTION){
                Intent confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if(confirm!=null){confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(confirm);}
            }else{
                String msg=code==PackageInstaller.STATUS_SUCCESS?"App installed successfully":"Install failed: "+intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(context,msg,Toast.LENGTH_LONG).show();
            }
        }
    }
}
