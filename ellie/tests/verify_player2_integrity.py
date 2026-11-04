"""The default interpreter MUST be the original Player2 implementation, byte-for-byte."""
from pathlib import Path
from zipfile import ZipFile
root=Path(__file__).resolve().parents[1]
engine='com/player2/playerengine/player2api/Player2APIService.class'
bridge='com/player2/playerengine/interpreter/LocalChatGPTBridge.class'
with ZipFile(root/'baseline/playerengine-acquisition-safety.jar') as base, ZipFile(root/'dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-CRAFT-RECOVERY.jar') as output:
    assert base.read(engine)==output.read(engine), 'Player2 API route was modified'
    assert bridge not in output.namelist(), 'Local bridge hook was retained'
print('Player2 interpreter verified: original class byte-for-byte; no LocalChatGPTBridge shipped')
