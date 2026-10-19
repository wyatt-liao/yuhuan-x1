"""Run with Blender: blender -b assets/Voloop-X1-CG.blend --python tools/check_scene.py"""
import bpy
import json
import math
from pathlib import Path

scene = bpy.context.scene
assert scene.frame_end == 1050 and scene.render.fps == 30
assert scene.render.resolution_percentage == 100
assert not any(obj.type == 'FONT' for obj in scene.objects)
assert all(image.packed_file for image in bpy.data.images
           if image.source == 'FILE' and image.users > 0)

previous = None
max_step = 0.0
min_up_y = 1.0
for frame in range(1, 1051):
    scene.frame_set(frame)
    basis = scene.camera.matrix_world.to_3x3()
    rotation = basis.to_quaternion().normalized()
    min_up_y = min(min_up_y, basis.col[1].y)
    if previous:
        step = math.degrees(2 * math.acos(min(1.0, abs(previous.dot(rotation)))))
        max_step = max(max_step, step)
    previous = rotation.copy()

assert min_up_y > 0.85, 'Camera has lost its world +Y up direction'
assert max_step < 5.0, 'Camera orientation jumps between frames'
scene.frame_set(982)
pitch = (bpy.data.objects['SW2 actuator'].location
         - bpy.data.objects['SW1 actuator'].location).length * 10
assert abs(pitch - 8.89) < 1e-5
report = {
    'frames': 1050, 'fps': 30,
    'resolution': [scene.render.resolution_x, scene.render.resolution_y],
    'maxCameraRotationDegreesPerFrame': max_step,
    'minimumCameraUpDotWorldY': min_up_y,
    'switchCenterPitchMm': pitch,
    'packedTextures': True, 'letteringAsGeometry': True,
}
target = Path(__file__).resolve().parent.parent / 'verification' / 'scene-check.json'
target.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report))
