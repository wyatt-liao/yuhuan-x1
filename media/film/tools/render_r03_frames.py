"""Render packed R03 scene; blank offscreen frames are reused without rendering."""
import bpy
import json
import shutil
import sys
import time
from pathlib import Path
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

out=Path(sys.argv[sys.argv.index('--')+1])
out.mkdir(parents=True,exist_ok=True)
scene=bpy.context.scene
root=bpy.data.objects['Yuhuan X1 R03 assembly']
phone=bpy.data.objects['Phone']
scene.render.image_settings.file_format='PNG'
scene.render.image_settings.color_mode='RGBA'
scene.render.resolution_percentage=100
scene.render.film_transparent=True
blank=out/'blank.png'
if not blank.exists():
    image=bpy.data.images.new('Transparent offscreen frame',width=1920,height=1080,alpha=True)
    image.generated_color=(0,0,0,0)
    image.filepath_raw=str(blank)
    image.file_format='PNG'
    image.save()

def visible(obj,w,h,d):
    points=[world_to_camera_view(scene,scene.camera,obj.matrix_world@Vector((x,y,z))) for x in [-w/2,w/2] for y in [-h/2,h/2] for z in [-d/2,d/2]]
    return max(v.z for v in points)>.1 and min(v.x for v in points)<1 and max(v.x for v in points)>0 and min(v.y for v in points)<1 and max(v.y for v in points)>0

start=time.time()
rendered=0
for frame in range(1,1051):
    target=out/('frame-%04d.png'%frame)
    if target.exists():
        continue
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    if visible(root,11.08,8.485,1.05) or visible(phone,3.6,7.3,.4):
        scene.render.filepath=str(target)
        bpy.ops.render.render(write_still=True)
        rendered+=1
    else:
        shutil.copyfile(blank,target)
    if frame%30==0:
        print(json.dumps({'frame':frame,'total':1050,'rendered':rendered,'elapsedSeconds':round(time.time()-start)}),flush=True)
print(json.dumps({'complete':True,'frames':1050,'rendered':rendered,'elapsedSeconds':round(time.time()-start)}),flush=True)
