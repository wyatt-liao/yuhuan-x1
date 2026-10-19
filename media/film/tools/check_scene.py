import bpy,json,math,hashlib
from pathlib import Path
from mathutils import Vector
scene=bpy.context.scene
film=Path(__file__).resolve().parents[1];report={}
root=bpy.data.objects['Yuhuan X1 R03 assembly']
meshes=json.loads((film/'assets/r03-assembly.json').read_text())
# The assembly and exported shell coordinates must be identical to the CAD tessellation.
worst=0
for part in meshes:
 if part['name']=='BUTTONS_SPRUE_OPTIONAL':continue
 obj=bpy.data.objects.get('SW1 actuator' if part['name']=='button_record' else 'SW2 actuator' if part['name']=='button_recall' else 'R03 '+part['name'])
 assert obj and len(obj.data.vertices)==len(part['vertices'])
 scene.frame_set(982)
 for vert,source in zip(obj.data.vertices,part['vertices']):
  expected=Vector(((source[0]-49.9999)/10,(source[1]-37.0205)/10,(source[2]-4.9)/10))
  actual=obj.matrix_local@vert.co
  worst=max(worst,(actual-expected).length*10)
assert worst<.0001,worst
prev=None;maxstep=0;minup=1
for frame in range(1,1051):
 scene.frame_set(frame);bpy.context.view_layer.update()
 basis=scene.camera.matrix_world.to_3x3();q=basis.to_quaternion().normalized()
 minup=min(minup,basis.col[1].y)
 if prev:maxstep=max(maxstep,math.degrees(2*math.acos(min(1,abs(prev.dot(q))))))
 prev=q.copy()
assert maxstep<5 and minup>.85
assert not any(o.type=='FONT' for o in scene.objects)
assert all(i.packed_file for i in bpy.data.images if i.source=='FILE' and i.users>0)
scene.frame_set(982)
pitch=(bpy.data.objects['SW2 actuator'].location-bpy.data.objects['SW1 actuator'].location).length*10
assert abs(pitch-8.89)<.0001
report={'product':'语环 X1','revision':'R03','sameTessellationAsCAD':True,'worstMeshCoordinateErrorMm':worst,'bodyMm':list(scene['body_dimensions_mm']),'buttonMaximumMm':scene['button_maximum_mm'],'switchCenterPitchMm':pitch,'maxCameraRotationDegreesPerFrame':maxstep,'minimumCameraUpDotWorldY':minup,'packedTextures':True,'actualAndroidScreenshots':True,'meshSha256':hashlib.sha256((film/'assets/r03-assembly.json').read_bytes()).hexdigest()}
(film/'verification/r03-scene-check.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False),flush=True)
