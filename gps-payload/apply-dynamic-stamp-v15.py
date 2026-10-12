from pathlib import Path
root=Path("gps-app/app/src/main/java/com/personal/gpsmapcamera")
m=root/"MainActivity.java"
s=m.read_text()
assert "private float stampTextScale=1f;" in s
s=s.replace("private float stampTextScale=1f;","private float stampTextScale=1f;\n    private float titleTextScale=1f;\n    private float detailsTextScale=1f;")
start=s.index("    private void changeStampTextSize() {")
end=s.index("    private void quickSettings()",start)
s=s[:start]+'''    private void changeStampTextSize() {
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22),dp(14),dp(22),dp(8));
        TextView heading=text("Main Address / Title Size",15,Color.WHITE,true);
        layout.addView(heading);
        SeekBar title=new SeekBar(this);
        title.setMax(180);
        title.setProgress(Math.round((titleTextScale-0.5f)*100));
        layout.addView(title);
        TextView details=text("Details / Coordinates / Date Size",15,Color.WHITE,true);
        layout.addView(details);
        SeekBar small=new SeekBar(this);
        small.setMax(180);
        small.setProgress(Math.round((detailsTextScale-0.5f)*100));
        layout.addView(small);
        new AlertDialog.Builder(this)
            .setTitle("GPS Address Bar Text Sizes (50–230%)")
            .setView(layout)
            .setPositiveButton("Apply",(d,w)->{
                titleTextScale=0.5f+title.getProgress()/100f;
                detailsTextScale=0.5f+small.getProgress()/100f;
                requestPreview();
            })
            .setNeutralButton("Reset",(d,w)->{
                titleTextScale=1f;detailsTextScale=1f;requestPreview();
            })
            .setNegativeButton("Cancel",null).show();
    }
''' +s[end:]
assert "opts.textScale=stampTextScale;" in s
s=s.replace("opts.textScale=stampTextScale;","opts.textScale=stampTextScale;\n        opts.titleTextScale=titleTextScale;\n        opts.detailsTextScale=detailsTextScale;")
m.write_text(s)

p=root/"StampRenderer.java"
s=p.read_text()
s=s.replace("public float textScale=1f;","public float textScale=1f;\n        public float titleTextScale=1f;\n        public float detailsTextScale=1f;")
start=s.index("        float boxH=",s.index("private static void videoStamp"))
end=s.index("        Paint pnt=new Paint",start)
s=s[:start]+'''        float titleFont=Math.max(10f,14.5f*scale)*1.43f*o.titleTextScale;
        float detailFont=Math.max(10f,14.5f*scale)*0.92f*o.detailsTextScale;
        float rowStep=Math.max(detailFont*1.3f,17f*scale);
        float titleStep=Math.max(titleFont*1.28f,24f*scale);
        float paddingTop=Math.max(10f*scale,titleFont*0.26f);
        float contentH=paddingTop+titleStep+rowStep*3f+Math.max(9f*scale,detailFont*0.55f);
        float boxH=Math.min(h*0.66f,Math.max(108f*scale,contentH));
        float top=o.top?edge:h-boxH-edge;
        float bottom=top+boxH;
        float mapW=o.showMap?Math.min(boxH*0.95f,w*0.29f):0f;
        float gap=8f*scale;
        float left=edge+mapW+(mapW>0?gap:0f);
        float right=w-edge;
''' +s[end:]
start=s.index("        float base=",s.index("private static void videoStamp"))
end=s.index("        String tag=",start)
s=s[:start]+'''        String address=o.address==null?"":o.address.trim();
        if(address.isEmpty())address="Selected GPS Location";
        pnt.setColor(Color.WHITE);
        pnt.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        pnt.setTextSize(titleFont);
        float y=top+paddingTop+titleFont;
        drawEllipsized(canvas,address,x,y,max,pnt);
        pnt.setTypeface(Typeface.DEFAULT);
        pnt.setTextSize(detailFont);
        y+=titleStep;
        String caption=(o.note==null||o.note.trim().isEmpty())?address:o.note.trim();
        drawEllipsized(canvas,caption,x,y,max,pnt);
        y+=rowStep;
        drawEllipsized(canvas,String.format(Locale.US,"Lat %.6f° Long %.6f°",o.latitude,o.longitude),x,y,max,pnt);
        y+=rowStep;
        String date=new SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a 'GMT' Z",Locale.US).format(new Date(o.timestamp));
        drawEllipsized(canvas,date,x,y,max,pnt);
''' +s[end:]
p.write_text(s)
print("Dynamic address box and independent title/details controls installed.")
