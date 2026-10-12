from pathlib import Path
p=Path("gps-app/app/src/main/java/com/personal/gpsmapcamera/MainActivity.java")
s=p.read_text()
s=s.replace("private EditText mapSearchField;","private EditText mapSearchField;\n    private EditText mapTitleEdit,mapAddressEdit,mapLatEdit,mapLonEdit;\n    private boolean resumeLocation=false;")
s=s.replace('        mapSearchField=field("Search village, city, address or lat, lon");','        mapSearchField=field("Search place, village, road or coordinates");')
s=s.replace('        mapSearchButton=button("⌕ SEARCH",true);','        mapSearchButton=button("➜",true);')
s=s.replace('mapSearchButton.setText("⌕ SEARCH");','mapSearchButton.setText("➜");')
s=s.replace('        mapScreen.addView(mapLabel);','        mapLabel.setVisibility(View.GONE);\n        mapScreen.addView(mapLabel);')
needle='        mapView.setOnLocationSelected((lat,lon)->locationPicked(lat,lon,false));'
insert='''        mapView.setOnLocationSelected((lat,lon)->locationPicked(lat,lon,false));
        LinearLayout details=new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(8),dp(6),dp(8),dp(6));
        details.setBackgroundColor(0xFF151519);
        mapTitleEdit=field("Custom location name");
        mapTitleEdit.setSingleLine(true);
        details.addView(mapTitleEdit,new LinearLayout.LayoutParams(-1,dp(43)));
        mapAddressEdit=field("Editable full address");
        mapAddressEdit.setSingleLine(true);
        details.addView(mapAddressEdit,new LinearLayout.LayoutParams(-1,dp(43)));
        LinearLayout coords=row();
        mapLatEdit=field("Latitude");
        mapLonEdit=field("Longitude");
        int number=android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;
        mapLatEdit.setInputType(number);mapLonEdit.setInputType(number);
        coords.addView(mapLatEdit,weighted(0,1));
        coords.addView(space(6),new LinearLayout.LayoutParams(dp(6),1));
        coords.addView(mapLonEdit,weighted(0,1));
        details.addView(coords,new LinearLayout.LayoutParams(-1,dp(43)));
        LinearLayout actions=row();
        Button add=button("Add Location",true),saveAddress=button("Save Address",false);
        actions.addView(add,weighted(0,1));
        actions.addView(space(6),new LinearLayout.LayoutParams(dp(6),1));
        actions.addView(saveAddress,weighted(0,1));
        details.addView(actions,new LinearLayout.LayoutParams(-1,dp(46)));
        mapScreen.addView(details);
        add.setOnClickListener(v->applyMapEdits(true));
        saveAddress.setOnClickListener(v->{if(applyMapEdits(false))savePlace();});'''
assert needle in s
s=s.replace(needle,insert)
marker='    private void buildMapScreen() {'
helpers='''    private void updateMapEditFields(){
        if(mapLatEdit==null)return;
        mapLatEdit.setText(String.format(Locale.US,"%.6f",latitude));
        mapLonEdit.setText(String.format(Locale.US,"%.6f",longitude));
    }
    private boolean applyMapEdits(boolean exit){
        try{
            double lat=Double.parseDouble(mapLatEdit.getText().toString().trim());
            double lon=Double.parseDouble(mapLonEdit.getText().toString().trim());
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new NumberFormatException();
            latitude=lat;longitude=lon;pointSelected=true;
            latText.setText(String.format(Locale.US,"%.7f",lat));
            lonText.setText(String.format(Locale.US,"%.7f",lon));
            mapView.setPoint(lat,lon);
            String title=mapTitleEdit.getText().toString().trim();
            String detail=mapAddressEdit.getText().toString().trim();
            addressText.setText(title.isEmpty()?detail:title);
            noteText.setText(detail.equals(title)?"":detail);
            requestPreview();
            if(exit)showEditor();
            return true;
        }catch(NumberFormatException e){toast("Enter valid latitude and longitude");return false;}
    }
'''
s=s.replace(marker,helpers+marker)
s=s.replace('        mapScreen.addView(use,buttonParams);','        use.setVisibility(View.GONE);\n        mapScreen.addView(use,buttonParams);')
s=s.replace('                        mapSearchField.setText(labels.get(which));','''                        mapSearchField.setText(labels.get(which));
                        mapTitleEdit.setText(labels.get(which));
                        mapAddressEdit.setText(labels.get(which));
                        updateMapEditFields();''')
s=s.replace('    private void showMap() {mapScreen.setVisibility(View.VISIBLE);editorScreen.setVisibility(View.GONE);}','    private void showMap() {mapScreen.setVisibility(View.VISIBLE);editorScreen.setVisibility(View.GONE);updateMapEditFields();}')
s=s.replace('        lookupAddress(lat,lon);\n        requestPreview();','''        updateMapEditFields();
        if(mapTitleEdit!=null){mapTitleEdit.setText("");mapAddressEdit.setText("");}
        lookupAddress(lat,lon);
        requestPreview();''')
s=s.replace('                              addressText.getText().toString().trim().isEmpty()) addressText.setText(address);','''                              addressText.getText().toString().trim().isEmpty()){
                            addressText.setText(address);
                            if(mapTitleEdit!=null&&mapTitleEdit.getText().toString().trim().isEmpty())mapTitleEdit.setText(address);
                            if(mapAddressEdit!=null&&mapAddressEdit.getText().toString().trim().isEmpty())mapAddressEdit.setText(address);
                        }''')
s=s.replace('    private void getLocation() {','''    @Override protected void onResume(){
        super.onResume();
        if(resumeLocation){resumeLocation=false;ui.postDelayed(this::requestLocation,500);}
    }
    private void openLocationSettings(){
        resumeLocation=true;
        try{startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));}
        catch(Exception e){toast("Open Settings > Location to enable GPS");}
    }
    private void getLocation() {''')
s=s.replace('            else {toast("Turn on Location / GPS in phone settings");return;}','''            else {
                new AlertDialog.Builder(this).setTitle("Enable Location Services")
                    .setMessage("Your phone Location is OFF. Enable Location for automatic detection. You can review app location permission in App Settings. Always Allow is managed by Android and is generally only offered to apps requiring background location.")
                    .setPositiveButton("Turn On Location",(d,w)->openLocationSettings())
                    .setNeutralButton("App Permission",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))))
                    .setNegativeButton("Not now",null).show();
                return;
            }''')
p.write_text(s)
print("GPS map editable search layout and permission prompts applied")
