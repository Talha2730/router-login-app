from pathlib import Path
p=Path("gps-app/app/src/main/java/com/personal/gpsmapcamera/StampRenderer.java")
s=p.read_text()
a=s.index("    /** Compact photo GPS overlay")
b=s.index("    private static void drawEllipsized",a)
method='''    /** Reference-style GPS stamp with left map and translucent address panel. */
    private static void videoStamp(Canvas canvas,int w,int h,Options o,TileRepository tiles) {
        float scale=w/690f;
        float edge=Math.max(3f,4f*scale);
        float boxH=Math.min(h*.27f,Math.max(108*scale,h*.098f));
        float top=o.top?edge:h-boxH-edge,bottom=top+boxH;
        float mapW=o.showMap?boxH*.95f:0f;
        float left=edge+mapW+(mapW>0?8*scale:0f),right=w-edge;
        Paint pnt=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        pnt.setColor(0xBA181818);
        canvas.drawRoundRect(left,top,right,bottom,5*scale,5*scale,pnt);
        if(mapW>0&&tiles!=null){
            RectF map=new RectF(edge,top,edge+mapW,bottom);
            canvas.save();canvas.clipRect(map);
            tiles.draw(canvas,map,o.latitude,o.longitude,Math.min(o.mapZoom,17),Math.max(145f,230*scale));
            float px=map.centerX(),py=map.centerY();
            pnt.setColor(0xFFE62E35);canvas.drawCircle(px,py-4*scale,8*scale,pnt);
            android.graphics.Path tip=new android.graphics.Path();
            tip.moveTo(px-6*scale,py);tip.lineTo(px,py+12*scale);
            tip.lineTo(px+6*scale,py);tip.close();canvas.drawPath(tip,pnt);
            pnt.setColor(Color.WHITE);canvas.drawCircle(px,py-4*scale,3*scale,pnt);
            pnt.setColor(0xB8000000);
            canvas.drawRect(map.left,map.bottom-12*scale,map.right,map.bottom,pnt);
            pnt.setColor(Color.WHITE);pnt.setTextSize(Math.max(7f,8f*scale));
            canvas.drawText("© OpenStreetMap",map.left+2*scale,map.bottom-3*scale,pnt);
            canvas.restore();
        }
        float x=left+10*scale,max=right-x-10*scale;
        float base=Math.max(10f,14.5f*scale)*o.textScale;
        String address=o.address==null?"":o.address.trim();
        if(address.isEmpty())address="Selected GPS Location";
        pnt.setColor(Color.WHITE);
        pnt.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        pnt.setTextSize(base*1.43f);
        drawEllipsized(canvas,address,x,top+27*scale,max,pnt);
        pnt.setTypeface(Typeface.DEFAULT);pnt.setTextSize(base*.92f);
        String caption=o.note==null||o.note.trim().isEmpty()?address:o.note.trim();
        drawEllipsized(canvas,caption,x,top+48*scale,max,pnt);
        drawEllipsized(canvas,String.format(Locale.US,"Lat %.6f° Long %.6f°",o.latitude,o.longitude),x,top+67*scale,max,pnt);
        String date=new SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a 'GMT' Z",Locale.US).format(new Date(o.timestamp));
        drawEllipsized(canvas,date,x,top+86*scale,max,pnt);
        String tag="GPS Map Camera";
        pnt.setTypeface(Typeface.DEFAULT);pnt.setTextSize(Math.max(8,10*scale));
        float tw=pnt.measureText(tag),th=19*scale;
        pnt.setColor(0xC22E3033);
        canvas.drawRoundRect(right-tw-13*scale,top-th,right,top+2*scale,4*scale,4*scale,pnt);
        pnt.setColor(Color.WHITE);
        canvas.drawText(tag,right-tw-7*scale,top-5*scale,pnt);
    }

'''
p.write_text(s[:a]+method+s[b:])
