from pathlib import Path
import sys

project=Path(sys.argv[1] if len(sys.argv)>1 else '/mnt/data/Personal_GPS_Map_Camera_Editor')
java=project/'app/src/main/java/com/personal/gpsmapcamera/MainActivity.java'
stamp=project/'app/src/main/java/com/personal/gpsmapcamera/StampRenderer.java'
gradle=project/'app/build.gradle'
s=java.read_text()
def replace(src,dst,count=1):
 global s
 if s.count(src)!=count:raise RuntimeError(f'MainActivity pattern count {s.count(src)} expected {count}: {src[:100]}')
 s=s.replace(src,dst)

replace('import android.widget.AdapterView;', '''import android.widget.AdapterView;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
''')
replace('private Spinner styleSpinner;', '''private Spinner styleSpinner;
    private EditText mapSearchField;
    private Button mapSearchButton;
    private volatile boolean searchingLocation=false;
    private long lastSearchTime=0;
    private float stampTextScale=1f;''')
replace('text("◉   PERSONAL GPS MAP CAMERA",20,WHITE,true)', 'text("◉   GPS MAP CAMERA EDITOR",19,WHITE,true)')
replace('text("Private • No ads • Save to your phone",12,0xFF9DB5C7,false)', 'text("Your personal photo editor  ·  No ads",12,0xFF9DB5C7,false)')
replace('imageParams.topMargin=dp(12);', '''imageParams.height=dp(Math.max(290,Math.min(465,(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density)-230)));
        imageParams.topMargin=dp(10);''')
replace('imageBox.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));', '''imageBox.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        // Video-style editor toolbar: main tools remain accessible while advanced options stay below.
        HorizontalScrollView tools=new HorizontalScrollView(this);
        tools.setHorizontalScrollBarEnabled(false);
        tools.setFillViewport(false);
        LinearLayout toolRow=row();
        toolRow.setPadding(0,dp(5),0,dp(4));
        addQuickTool(toolRow,"▣ Image",()->selectPhoto());
        addQuickTool(toolRow,"⌕ Search",()->focusMapSearch());
        addQuickTool(toolRow,"⌖ Location",()->showMap());
        addQuickTool(toolRow,"▣ Date/Time",()->pickDateTime());
        addQuickTool(toolRow,"▤ Template",()->chooseTemplate());
        addQuickTool(toolRow,"✎ Customize",()->customizeStamp());
        addQuickTool(toolRow,"Tt Text Size",()->changeStampTextSize());
        addQuickTool(toolRow,"⚙ Settings",()->quickSettings());
        addQuickTool(toolRow,"✓ Save",()->savePhoto());
        addQuickTool(toolRow,"▧ Recent",()->openLastSaved());
        tools.addView(toolRow);
        content.addView(tools,marginTop(6));''')
replace('new String[]{"Classic • dark","Reporting • light","Minimal • compact"}', 'new String[]{"Classic • dark","Reporting • light","Minimal • compact","Video • map on left"}')
replace('styleSpinner.setAdapter(adapter);','''styleSpinner.setAdapter(adapter);
        styleSpinner.setSelection(3);''')
replace('''    private void buildMapScreen() {
        mapLabel=''', '''    private void buildMapScreen() {
        LinearLayout searchArea=row();
        searchArea.setPadding(dp(10),dp(7),dp(10),dp(4));
        mapSearchField=field("Search village, city, address or lat, lon");
        mapSearchField.setSingleLine(true);
        mapSearchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        mapSearchField.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH){searchMapLocation();return true;}
            return false;
        });
        searchArea.addView(mapSearchField,weighted(0,3));
        searchArea.addView(space(7),new LinearLayout.LayoutParams(dp(7),1));
        mapSearchButton=button("⌕ SEARCH",true);
        searchArea.addView(mapSearchButton,weighted(0,1));
        mapSearchButton.setOnClickListener(v->searchMapLocation());
        mapScreen.addView(searchArea);
        mapLabel=''',1)
replace('''        mapTools.addView(gps,weighted(0,1));
        mapScreen.addView(mapTools);''', '''        mapTools.addView(gps,weighted(0,1));
        mapScreen.addView(mapTools);''')
replace('''    private void showEditor() {editorScreen.setVisibility(View.VISIBLE);mapScreen.setVisibility(View.GONE);}''', '''    private void addQuickTool(LinearLayout container,String label,Runnable action) {
        Button b=button(label,false);
        b.setTextSize(11);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(104),dp(51));
        p.setMargins(0,0,dp(6),0);
        container.addView(b,p);
        b.setOnClickListener(v->action.run());
    }

    private void focusMapSearch() {
        showMap();
        mapSearchField.requestFocus();
        mapSearchField.postDelayed(()->{
            InputMethodManager keyboard=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(keyboard!=null) keyboard.showSoftInput(mapSearchField,InputMethodManager.SHOW_IMPLICIT);
        },200);
    }

    private void searchMapLocation() {
        String query=mapSearchField.getText().toString().trim();
        if(query.length()<2){toast("Enter a village, city or address to search");return;}
        if(searchingLocation){toast("Please wait for current search");return;}
        // Coordinate input also works offline: e.g. 31.5204, 74.3587
        String[] parts=query.split(",");
        if(parts.length==2){
            try {
                double a=Double.parseDouble(parts[0].trim()),b=Double.parseDouble(parts[1].trim());
                if(a>=-90&&a<=90&&b>=-180&&b<=180){
                    locationPicked(a,b,false);hideKeyboard();toast("Coordinates selected");return;
                }
            }catch(NumberFormatException ignored) { }
        }
        // Nominatim explicitly prohibits autocomplete: requests are made only after Search is pressed.
        long elapsed=System.currentTimeMillis()-lastSearchTime;
        if(elapsed<1100){toast("Wait one second between searches");return;}
        lastSearchTime=System.currentTimeMillis();
        searchingLocation=true;
        mapSearchButton.setEnabled(false);
        mapSearchButton.setText("WAIT…");
        mapLabel.setText("Searching for: "+query);
        hideKeyboard();
        worker.execute(()->{
            final ArrayList<String> labels=new ArrayList<>();
            final ArrayList<Double> latitudes=new ArrayList<>(),longitudes=new ArrayList<>();
            String problem=null;
            HttpURLConnection connection=null;
            try {
                String path="https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=7&q="+
                        URLEncoder.encode(query,"UTF-8");
                connection=(HttpURLConnection)new URL(path).openConnection();
                connection.setRequestProperty("User-Agent","PersonalGPSMapCameraEditor/1.1 (Android personal map search)");
                connection.setRequestProperty("Accept","application/json");
                connection.setRequestProperty("Accept-Language",Locale.getDefault().toLanguageTag());
                connection.setConnectTimeout(11000);connection.setReadTimeout(11000);
                int status=connection.getResponseCode();
                if(status!=200)throw new IOException("Search service returned "+status);
                try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[4096];int n;
                    while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>180000)throw new IOException("Response too large");}
                    JSONArray found=new JSONArray(out.toString("UTF-8"));
                    for(int i=0;i<found.length();i++){
                        JSONObject p=found.getJSONObject(i);
                        labels.add(p.optString("display_name","Unknown place"));
                        latitudes.add(p.getDouble("lat"));longitudes.add(p.getDouble("lon"));
                    }
                }
            }catch(Exception e){problem=e.getMessage()==null?"Network error":e.getMessage();}
            finally {if(connection!=null)connection.disconnect();}
            final String error=problem;
            ui.post(()->{
                searchingLocation=false;mapSearchButton.setEnabled(true);mapSearchButton.setText("⌕ SEARCH");
                if(error!=null){mapLabel.setText("Search failed. Check internet and retry.");toast("Search unavailable: "+error);return;}
                if(labels.isEmpty()){mapLabel.setText("No places found. Try a longer address.");toast("No matching places found");return;}
                mapLabel.setText(labels.size()+" results for: "+query);
                new AlertDialog.Builder(this).setTitle("Choose location")
                    .setItems(labels.toArray(new String[0]),(dialog,which)->{
                        locationPicked(latitudes.get(which),longitudes.get(which),false);
                        addressText.setText(labels.get(which));
                        mapSearchField.setText(labels.get(which));
                        mapLabel.setText("Selected: "+labels.get(which));
                    }).setNegativeButton("Cancel",null).show();
            });
        });
    }

    private void hideKeyboard() {
        InputMethodManager keyboard=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(keyboard!=null)keyboard.hideSoftInputFromWindow(mapSearchField.getWindowToken(),0);
        mapSearchField.clearFocus();
    }

    private void chooseTemplate() {
        final String[] names={"Classic dark","Reporting light","Minimal compact","Video style • map on left"};
        new AlertDialog.Builder(this).setTitle("GPS Photo Template")
           .setSingleChoiceItems(names,styleSpinner.getSelectedItemPosition(),(dialog,index)->{
               styleSpinner.setSelection(index);requestPreview();dialog.dismiss();
           }).setNegativeButton("Cancel",null).show();
    }
    private void customizeStamp() {
        final EditText note=field("Caption / personal note");
        note.setText(noteText.getText());
        new AlertDialog.Builder(this).setTitle("Customize Photo Stamp")
            .setMessage("Add optional text to your GPS photo.")
            .setView(note)
            .setPositiveButton("Apply",(d,w)->{noteText.setText(note.getText());requestPreview();})
            .setNegativeButton("Cancel",null).show();
    }
    private void changeStampTextSize() {
        final SeekBar size=new SeekBar(this);
        size.setMax(100);size.setProgress(Math.round((stampTextScale-0.7f)*100));
        new AlertDialog.Builder(this).setTitle("Text Size (70–170%)")
            .setView(size)
            .setPositiveButton("Apply",(d,w)->{
                stampTextScale=0.7f+size.getProgress()/100f;requestPreview();
            }).setNegativeButton("Cancel",null).show();
    }
    private void quickSettings() {
        final String[] settings={"Show mini map on photo","Stamp at top of photo"};
        boolean[] checked={mapCheckbox.isChecked(),topCheckbox.isChecked()};
        new AlertDialog.Builder(this).setTitle("Photo Map Settings")
            .setMultiChoiceItems(settings,checked,(d,index,value)->checked[index]=value)
            .setPositiveButton("Apply",(d,w)->{
                mapCheckbox.setChecked(checked[0]);topCheckbox.setChecked(checked[1]);requestPreview();
            }).setNegativeButton("Cancel",null).show();
    }

    private void showEditor() {editorScreen.setVisibility(View.VISIBLE);mapScreen.setVisibility(View.GONE);}''')
replace('''        opts.mapZoom=mapView.getZoom();''', '''        opts.mapZoom=mapView.getZoom();
        opts.textScale=stampTextScale;''')
java.write_text(s)

r=stamp.read_text()
def rep(src,dst):
 global r
 if r.count(src)!=1:raise RuntimeError(f'Stamp pattern {src[:90]} count {r.count(src)}')
 r=r.replace(src,dst)
rep('''        public boolean showMap=true;''','''        public boolean showMap=true;
        public float textScale=1f;''')
rep('''        float s=Math.min(w/1000f,h/650f);''','''        if(o.style==3){ videoStamp(canvas,w,h,o,tiles);return; }
        float s=Math.min(w/1000f,h/650f);''')
rep('''        float font=Math.max(11*s,9.5f);''','''        float font=Math.max(11*s,9.5f)*o.textScale;''')
needle='''    private static void drawEllipsized(Canvas canvas,String raw,float x,float y,float max,Paint paint) {'''
if r.count(needle)!=1:raise RuntimeError('stamp insertion fail')
extra='''    /** Compact photo GPS overlay inspired by the reference video's mini-map-left layout. */
    private static void videoStamp(Canvas canvas,int w,int h,Options o,TileRepository tiles) {
        float s=Math.max(.5f,Math.min(w/900f,h/1300f));
        float margin=Math.max(6f,9f*s);
        float boxH=Math.min(h*.22f,182f*s);
        boxH=Math.max(66f,boxH);
        float top=o.top?margin:h-margin-boxH;
        float right=w-margin;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        p.setColor(0xD20A1320);
        canvas.drawRoundRect(margin,top,right,top+boxH,9f*s,9f*s,p);
        float mapW=o.showMap?Math.min(boxH-10*s,(right-margin)*.24f):0;
        if(mapW>0&&tiles!=null){
            RectF area=new RectF(margin+5*s,top+5*s,margin+5*s+mapW,top+boxH-5*s);
            canvas.save();canvas.clipRect(area);
            tiles.draw(canvas,area,o.latitude,o.longitude,Math.min(o.mapZoom,17),Math.max(100,256*s));
            p.setColor(0xFFEF4C50);canvas.drawCircle(area.centerX(),area.centerY(),6*s,p);
            p.setColor(Color.WHITE);canvas.drawCircle(area.centerX(),area.centerY(),2.5f*s,p);
            p.setColor(0xC0000000);canvas.drawRect(area.left,area.bottom-18*s,area.right,area.bottom,p);
            p.setColor(Color.WHITE);p.setTextSize(Math.max(7,9*s));
            canvas.drawText("© OpenStreetMap",area.left+2*s,area.bottom-6*s,p);
            canvas.restore();
        }
        float x=margin+12*s+(mapW>0?mapW+7*s:0);
        float max=right-x-9*s;
        float font=Math.max(10f,17f*s)*o.textScale;
        Paint txt=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        txt.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        txt.setColor(Color.WHITE);txt.setTextSize(font*1.14f);
        String address=o.address==null||o.address.trim().isEmpty()?"Selected GPS location":o.address.trim();
        drawEllipsized(canvas,address,x,top+30*s,max,txt);
        txt.setTypeface(Typeface.DEFAULT);txt.setColor(0xFFE0E7EF);txt.setTextSize(font);
        drawEllipsized(canvas,String.format(Locale.US,"%.6f, %.6f",o.latitude,o.longitude),x,top+65*s,max,txt);
        drawEllipsized(canvas,formatDate(o.timestamp),x,top+98*s,max,txt);
        if(o.note!=null&&!o.note.trim().isEmpty()) {
            txt.setColor(0xFF9FEBE1);
            drawEllipsized(canvas,o.note.trim(),x,top+130*s,max,txt);
        }
    }

'''
r=r.replace(needle,extra+needle)
stamp.write_text(r)
g=gradle.read_text().replace('versionCode 1','versionCode 2').replace("versionName '1.0.0'","versionName '1.1.0'")
gradle.write_text(g)
print('Updated GPS Editor v1.1 source in',project)