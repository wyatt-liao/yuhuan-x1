import bpy, json, math, sys
from pathlib import Path
from mathutils import Vector, Matrix

args=sys.argv[sys.argv.index('--')+1:]
views=set(args[1:])
out=Path(args[0]);meshes=json.loads((out/'preview/render-meshes.json').read_text())
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
scene=bpy.context.scene
scene.render.engine='CYCLES';scene.cycles.samples=32;scene.cycles.use_denoising=True
scene.render.resolution_x=1600;scene.render.resolution_y=1200;scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
scene.world.color=(.8,.8,.8)
world=scene.world;world.use_nodes=True;world.node_tree.nodes['Background'].inputs['Color'].default_value=(.75,.80,.77,1)
world.node_tree.nodes['Background'].inputs['Strength'].default_value=.45
scene.view_settings.view_transform='AgX'
scene.view_settings.look='AgX - Medium High Contrast'
scene.view_settings.exposure=-.6
objects={}
def mat(name,color,metal=0):
    m=bpy.data.materials.new(name);m.diffuse_color=color;m.use_nodes=True
    b=m.node_tree.nodes.get('Principled BSDF');b.inputs['Base Color'].default_value=color
    b.inputs['Roughness'].default_value=.40 if metal else .55;b.inputs['Metallic'].default_value=metal
    return m
for d in meshes:
    m=bpy.data.meshes.new(d['name']);m.from_pydata([[v/1000 for v in row] for row in d['vertices']],[],d['faces']);m.update()
    o=bpy.data.objects.new(d['name'],m);bpy.context.collection.objects.link(o)
    ismetal=('metal' in d['name'] or d['name'] in ['J1','J2']) and 'NYLON' not in d['name']
    o.data.materials.append(mat(d['name'],d['color'],.72 if ismetal else 0))
    # CAD 曲面已经细分，保持所有真实开孔边界。
    objects[d['name']]=o
    if d['name']=='BUTTONS_SPRUE_OPTIONAL':o.hide_render=True
def light(name,loc,power,size):
    data=bpy.data.lights.new(name,'AREA');data.energy=power;data.shape='DISK';data.size=size
    o=bpy.data.objects.new(name,data);bpy.context.collection.objects.link(o);o.location=loc
    o.rotation_euler=(Vector((.05,.037,.004))-o.location).to_track_quat('-Z','Y').to_euler()
light('key',(.04,-.09,.24),3,.20)
light('fill',(-.14,.10,.16),2,.17)
light('rim',(.15,.19,.13),3,.12)
groundmat=mat('ground',(.55,.58,.61,1))
bpy.ops.mesh.primitive_plane_add(size=2,location=(.05,.037,-.002));ground=bpy.context.object;ground.name='ground';ground.data.materials.append(groundmat)
cam_data=bpy.data.cameras.new('camera');cam=bpy.data.objects.new('camera',cam_data);bpy.context.collection.objects.link(cam);scene.camera=cam
cam_data.type='ORTHO';cam_data.lens=50;cam_data.clip_start=.001;cam_data.clip_end=10
def camera(loc,target,scale):
    cam.location=loc;cam.rotation_euler=(Vector(target)-cam.location).to_track_quat('-Z','Y').to_euler();cam_data.ortho_scale=scale
def render(name):
    if views and name not in views:return
    scene.render.filepath=str(out/'preview'/name);bpy.ops.render.render(write_still=True)
camera((.16,-.14,.24),(.05,.037,.004),.17)
render('01-front.png')
# 背面朝上，完整展示四声孔和凹入螺母，仍来自同一套实体几何。
center=Vector((.05,.037,.0049));flip=Matrix.Translation(center)@Matrix.Rotation(math.pi,4,'X')@Matrix.Translation(-center)
for o in objects.values():o.matrix_world=flip
camera((.15,-.12,.18),(.05,.037,.004),.17)
render('02-back.png')
for o in objects.values():o.matrix_world=Matrix.Identity(4)
# 爆炸图只移动组件，不改几何；电池穿过 PCB 开槽。
for name,o in objects.items():
    if name.startswith('lid') or name.startswith('button') or 'M2x8_' in name:o.location.z=.038
    elif not name.startswith('base') and 'M2_nut_' not in name:o.location.z=.014
    if 'rf_plastic' in name:o.location+=Vector((.012,.009,.003))
    if name.startswith('button'):o.location+=Vector((.012,.009,.018))
camera((.18,-.19,.25),(.055,.040,.026),.215)
render('03-exploded.png')
# 局部透视：隐藏上盖和按钮，展示电池嵌槽与独立的下沉焊脚位。
for name,o in objects.items():
    o.location=Vector((0,0,0))
    if name.startswith('lid') or name.startswith('button') or 'M2x8_' in name:o.hide_render=True
camera((.14,-.10,.22),(.05,.037,.004),.165)
render('04-inside.png')
# 分件接头视图：上盖翻转，显示平面榫接和按键导向。
for name,o in objects.items():
    o.hide_render=not(name.startswith('lid') or name.startswith('button'))
    if name.startswith('button'):o.hide_render=True
    o.matrix_world=flip
    if 'rf_plastic' in name:o.location+=Vector((.008,-.008,0))
camera((.14,-.10,.22),(.05,.037,.004),.17)
render('05-joint-detail.png')
for name,o in objects.items():
    o.hide_render=name=='BUTTONS_SPRUE_OPTIONAL';o.matrix_world=Matrix.Identity(4)
camera((.05,.0370205,.30),(.05,.0370205,.004),.15)
render('06-top.png')
for name,o in objects.items():
    o.hide_render=name!='BUTTONS_SPRUE_OPTIONAL'
camera((.045,-.040,.065),(.013,.007,.0025),.044)
render('08-buttons-sprue.png')
print('ENCLOSURE RENDERS COMPLETE')
