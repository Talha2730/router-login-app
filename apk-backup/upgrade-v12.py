from pathlib import Path
p=Path('apk-backup/app/src/main/java/com/personal/apkbackup/MainActivity.java')
s=p.read_text()
s=s.replace('import android.graphics.Typeface;','import android.graphics.Typeface;\nimport android.graphics.drawable.GradientDrawable;\nimport android.text.*;\nimport androidx.core.content.FileProvider;')
s=s.replace('private LinearLayout list,root;','private LinearLayout list,root;\n    private EditText search;\n    private Spinner sort;\n    private String query="";\n    private int sortMode=0;')
s=s.replace('label("Personal APK Backup",24,true);','label("APK BACKUP",27,true);')
s=s.replace('label("Save & share installable APK files • No receiver app required",12,false);','label("Backup • Share • Install on another phone",13,false);\n        search=new EditText(this);search.setSingleLine(true);search.setTextColor(Color.WHITE);search.setHintTextColor(0xFFA7B9C5);search.setHint("Search applications");search.setTextSize(16);search.setPadding(22,4,16,4);search.setBackground(round(PANEL,16));root.addView(search,new LinearLayout.LayoutParams(-1,58));\n        sort=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Name A–Z","Newest installed","Oldest installed","Recently updated"});sort.setAdapter(adapter);root.addView(sort,new LinearLayout.LayoutParams(-1,48));')
s=s.replace('        refresh();\n    }\n    private TextView label', '''        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence t,int st,int c,int a){}
            public void onTextChanged(CharSequence t,int st,int before,int count){query=t.toString().toLowerCase(Locale.ROOT);refresh();}
            public void afterTextChanged(Editable e){}
        });
        sort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){sortMode=pos;refresh();}
        });
        refresh();
    }
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius*getResources().getDisplayMetrics().density);return d;}
    private TextView label''')
s=s.replace('            if(a.sourceDir==null)continue;\n            apps.add(a);','            if(a.sourceDir==null)continue;\n            if(!pm.getApplicationLabel(a).toString().toLowerCase(Locale.ROOT).contains(query)&&!a.packageName.toLowerCase(Locale.ROOT).contains(query))continue;\n            apps.add(a);')
s=s.replace('        Collections.sort(apps,(a,b)->pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));','''        Collections.sort(apps,(a,b)->{
            if(sortMode!=0){
                try{
                    PackageInfo ap=pm.getPackageInfo(a.packageName,0),bp=pm.getPackageInfo(b.packageName,0);
                    long x=sortMode==3?ap.lastUpdateTime:ap.firstInstallTime,y=sortMode==3?bp.lastUpdateTime:bp.firstInstallTime;
                    int cmp=Long.compare(x,y);if(sortMode==1||sortMode==3)cmp=-cmp;
                    if(cmp!=0)return cmp;
                }catch(Exception ignored){}
            }
            return pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString());
        });''')
s=s.replace('            row.setBackgroundColor(PANEL);','            row.setBackground(round(PANEL,15));')
s=s.replace('            row.addView(sub);','''            row.addView(sub);
            try{
                PackageInfo meta=pm.getPackageInfo(a.packageName,0);
                TextView date=new TextView(this);date.setText("Installed "+new java.text.SimpleDateFormat("dd MMM yyyy",Locale.US).format(new Date(meta.firstInstallTime)));
                date.setTextColor(0xFF8FAAB3);date.setTextSize(11);row.addView(date);
            }catch(Exception ignored){}
            LinearLayout actions=new LinearLayout(this);actions.setOrientation(0);
            Button backup=button("BACKUP"),share=button("SHARE APK");
            actions.addView(backup,new LinearLayout.LayoutParams(0,48,1));actions.addView(share,new LinearLayout.LayoutParams(0,48,1));
            row.addView(actions);
            backup.setOnClickListener(v->chooseExport(a));
            share.setOnClickListener(v->shareDirect(a));''')
s=s.replace('            row.setOnClickListener(v->chooseExport(a));','            row.setOnClickListener(v->chooseExport(a));')
key='    private void chooseExport(ApplicationInfo a){'
assert key in s
s=s.replace(key,'''    private void shareDirect(ApplicationInfo a){
        message("Preparing APK for sharing...");
        new Thread(()->{
            File dir=new File(getCacheDir(),"shared");dir.mkdirs();
            boolean split=a.splitSourceDirs!=null&&a.splitSourceDirs.length>0;
            File file=new File(dir,a.packageName+(split?".apks":".apk"));
            try(OutputStream output=new FileOutputStream(file)){
                if(split){
                    ZipOutputStream zip=new ZipOutputStream(output);
                    writeZip(zip,"base.apk",a.sourceDir);
                    for(int j=0;j<a.splitSourceDirs.length;j++)writeZip(zip,"split_"+j+".apk",a.splitSourceDirs[j]);
                    zip.finish();zip.flush();
                }else copy(new FileInputStream(a.sourceDir),output);
                Uri uri=FileProvider.getUriForFile(this,getPackageName()+".provider",file);
                runOnUiThread(()->{
                    if(split)Toast.makeText(this,"Split APK requires an installer on the other phone",Toast.LENGTH_LONG).show();
                    shareBackup(uri,split);
                });
            }catch(Exception e){runOnUiThread(()->message("Share failed: "+e.getMessage()));}
        }).start();
    }
'''+key)
p.write_text(s)
print('Applied modern search, date sort, direct share')
