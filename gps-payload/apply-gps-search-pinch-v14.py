from pathlib import Path
r=Path("gps-app/app/src/main/java/com/personal/gpsmapcamera")
p=r/"MainActivity.java"
s=p.read_text()
s=s.replace("        // Ask for GPS access only when the user presses CURRENT GPS.","        ui.postDelayed(this::requestLocation,400);")
s=s.replace("        mapScreen.addView(mapTools);","        mapTools.setVisibility(View.GONE);\n        mapScreen.addView(mapTools);")
s=s.replace("Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);","Intent i=new Intent(Intent.ACTION_GET_CONTENT);")
s=s.replace("if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED&&\n                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) provider=LocationManager.GPS_PROVIDER;\n            else if(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))provider=LocationManager.NETWORK_PROVIDER;", "if(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))provider=LocationManager.NETWORK_PROVIDER;\n            else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED&&manager.isProviderEnabled(LocationManager.GPS_PROVIDER))provider=LocationManager.GPS_PROVIDER;")
s=s.replace("manager.requestLocationUpdates(provider,500,0,gpsListener,Looper.getMainLooper());","Location cached=manager.getLastKnownLocation(provider);\n            if(cached!=null&&System.currentTimeMillis()-cached.getTime()<120000&&cached.hasAccuracy()&&cached.getAccuracy()<1000)locationPicked(cached.getLatitude(),cached.getLongitude(),true);\n            manager.requestLocationUpdates(provider,1000,0,gpsListener,Looper.getMainLooper());")
needle="            finally {if(connection!=null)connection.disconnect();}"
repl="""            finally {if(connection!=null)connection.disconnect();}
            if(labels.isEmpty()){
                try{
                    android.location.Geocoder geo=new android.location.Geocoder(this,Locale.getDefault());
                    java.util.List<android.location.Address> matches=geo.getFromLocationName(query,7);
                    if(matches!=null)for(android.location.Address a:matches){
                        if(!a.hasLatitude()||!a.hasLongitude())continue;
                        String name=a.getAddressLine(0);
                        labels.add(name!=null?name:query);
                        latitudes.add(a.getLatitude());longitudes.add(a.getLongitude());
                    }
                    if(!labels.isEmpty())problem=null;
                }catch(Exception ignored){}
            }"""
assert needle in s
s=s.replace(needle,repl)
p.write_text(s)
p=r/"MapTileView.java"
s=p.read_text().replace("import android.view.View;","import android.view.View;\nimport android.view.ScaleGestureDetector;")
s=s.replace("    private final float density;","    private final float density;\n    private final ScaleGestureDetector pinch;\n    private float scaleAccum=1f;")
s=s.replace("        density = getResources().getDisplayMetrics().density;","""        density = getResources().getDisplayMetrics().density;
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){
                scaleAccum*=d.getScaleFactor();
                if(scaleAccum>1.35f){changeZoom(1);scaleAccum=1f;}
                else if(scaleAccum<0.74f){changeZoom(-1);scaleAccum=1f;}
                return true;
            }
            @Override public boolean onScaleBegin(ScaleGestureDetector d){scaleAccum=1f;return true;}
        });""")
s=s.replace("        float x=event.getX(), y=event.getY();","        pinch.onTouchEvent(event);\n        if(event.getPointerCount()>1||pinch.isInProgress()){moved=true;return true;}\n        float x=event.getX(), y=event.getY();")
p.write_text(s)
