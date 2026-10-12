from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
p=Path("apk-backup/app/src/main/res/drawable-nodpi")
p.mkdir(parents=True,exist_ok=True)
im=Image.new("RGBA",(512,512),(14,24,33,255))
d=ImageDraw.Draw(im)
d.rounded_rectangle((18,18,494,494),radius=95,fill=(22,174,169),outline=(180,250,240),width=7)
d.rounded_rectangle((110,80,405,302),radius=28,fill=(9,60,72))
d.rectangle((180,52,335,100),fill=(9,60,72))
try:
    top=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",115)
    bottom=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",63)
except OSError:
    top=ImageFont.load_default()
    bottom=top
for text,y,font in [("APK",132,top),("BACKUP",343,bottom)]:
    box=d.textbbox((0,0),text,font=font)
    x=(512-(box[2]-box[0]))/2
    d.text((x,y),text,font=font,fill="white")
im.save(p/"apk_backup_icon.png")
print("APK BACKUP launcher icon generated")
