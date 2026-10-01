import json
import re
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCE_ROOT = ROOT / "src" / "main" / "resources"
ARCHIVE_ROOT = Path.home() / "Downloads"
ARCHIVES = [
	"FreshAnimations_v1.10.5.zip",
	"FA+All_Extensions-v1.9.2.zip",
	"FA+Quivers-v2.2.zip",
	"FA+Creepers-v2.1.zip",
	"FA+Spiders-v2.2.zip",
	"FA+Emissive-v1.6.zip",
	"FA+Objects-v2.1.2.zip",
	"FA+Details-v2.3.zip",
]
BABY_ANIMALS_ARCHIVE = "Fresh Animations x Baby Animals Remastered v0.1.zip"
PLAYER_ANIMATIONS_ARCHIVE = "FA+Player-v1.1.zip"
OUTDATED_BABY_MODELS = {
	"chicken_baby.jem",
	"cold_chicken_baby.jem",
	"warm_chicken_baby.jem",
	"mooshroom_baby.jem",
	"sheep_baby.jem",
	"sheep_baby_wool.jem",
	"sheep_baby_wool_undercoat.jem",
	"wolf_baby.jem",
	"wolf_baby_collar.jem",
}
OUTDATED_BABY_TEXTURES = {
	"chicken/chicken_baby.png",
	"chicken/cold_chicken_baby.png",
	"sheep/sheep_baby.png",
	"sheep/sheep_baby_wool.png",
	"sheep/sheep_baby_wool_undercoat.png",
	"wolf/pup_collar.png",
	"wolf/wolf_baby.png",
}
PRESERVE_CUSTOM_MODELS = {
	"villager.jem",
	"villager.properties",
	"villager2.jem",
	"villager3.jem",
	"villager4.jem",
	"villager5.jem",
	"villager6.jem",
	"villager_baby.jem",
	"wandering_trader.jem",
	"sheep.properties",
	"sheep_wool.properties",
	"sheep_wool_undercoat.properties",
	"sheep2.jem",
	"sheep3.jem",
	"sheep_wool2.jem",
	"sheep_wool_undercoat2.jem",
}


def asset_path(entry_name):
	match = re.match(r"^(21-\d+)/(assets|data)/(.+)$", entry_name)
	if match:
		return f"{match.group(2)}/{match.group(3)}"
	if entry_name.startswith(("assets/", "data/")):
		return entry_name
	return None


def is_conflicting_villager_asset(relative_path):
	parts = relative_path.parts
	if parts[:4] in {
		("assets", "minecraft", "optifine", "random"),
		("assets", "minecraft", "textures", "entity"),
	}:
		if "villager" in parts or "wandering_trader" in parts:
			return True
	if parts[:4] == ("assets", "minecraft", "emf", "cem"):
		name = relative_path.name.lower()
		return (name.startswith("villager") and not name.startswith("zombie_villager")) or name.startswith("wandering_trader")
	if parts[:4] == ("assets", "minecraft", "optifine", "cem"):
		name = relative_path.name.lower()
		return (name.startswith("villager") and not name.startswith("zombie_villager")) or name.startswith("wandering_trader")
	return False


def is_conflicting_player_asset(relative_path):
	if relative_path.parts[:4] not in {
		("assets", "minecraft", "emf", "cem"),
		("assets", "minecraft", "optifine", "cem"),
	}:
		return False
	name = relative_path.name.lower()
	return name.startswith("player") or name.startswith("a_player_") or name == "elytra.jem"


def remove_competing_emf_villager_models():
	model_root = RESOURCE_ROOT / "assets/minecraft/emf/cem"
	if not model_root.is_dir():
		return
	for model_path in model_root.iterdir():
		name = model_path.name.lower()
		if model_path.is_file() and (
			(name.startswith("villager") and not name.startswith("zombie_villager"))
			or name.startswith("wandering_trader")
		):
			model_path.unlink()


def import_baby_animal_models():
	archive_path = ARCHIVE_ROOT / BABY_ANIMALS_ARCHIVE
	if not archive_path.is_file():
		raise FileNotFoundError(f"Missing supplied baby animal archive: {archive_path}")
	written = 0
	with zipfile.ZipFile(archive_path) as archive:
		for entry in archive.infolist():
			if entry.is_dir():
				continue
			relative_name = asset_path(entry.filename)
			if relative_name is None:
				continue
			relative_path = Path(*relative_name.split("/"))
			# Keep only the actual baby models and textures they use.
			is_baby_model = (
				relative_path.parts[:4] == ("assets", "minecraft", "optifine", "cem")
				and "baby" in relative_path.name.lower()
				and relative_path.suffix.lower() == ".jem"
			)
			is_baby_texture = (
				relative_path.parts[:4] == ("assets", "minecraft", "textures", "entity")
				and ("baby" in relative_path.name.lower() or relative_path.name == "pup_collar.png")
			)
			if not (is_baby_model or is_baby_texture):
				continue
			if is_baby_model and relative_path.name in OUTDATED_BABY_MODELS:
				continue
			target = (RESOURCE_ROOT / relative_path).resolve()
			if not target.is_relative_to(RESOURCE_ROOT.resolve()):
				raise RuntimeError(f"Archive entry escapes resources directory: {entry.filename}")
			target.parent.mkdir(parents=True, exist_ok=True)
			target.write_bytes(archive.read(entry))
			written += 1
	for model_name in OUTDATED_BABY_MODELS:
		(RESOURCE_ROOT / "assets/minecraft/optifine/cem" / model_name).unlink(missing_ok=True)
	return written


def import_player_animation_models():
	archive_path = ARCHIVE_ROOT / PLAYER_ANIMATIONS_ARCHIVE
	if not archive_path.is_file():
		raise FileNotFoundError(f"Missing supplied player animation archive: {archive_path}")
	written = 0
	with zipfile.ZipFile(archive_path) as archive:
		entries = sorted(
			archive.infolist(),
			key=lambda entry: (
				0 if entry.filename.startswith("assets/")
				else 1 if entry.filename.startswith("21-2/")
				else 2,
				entry.filename,
			),
		)
		for entry in entries:
			if entry.is_dir():
				continue
			relative_name = asset_path(entry.filename)
			if relative_name is None:
				continue
			relative_path = Path(*relative_name.split("/"))
			if relative_path.parts[:4] != ("assets", "minecraft", "emf", "cem"):
				continue
			if relative_path.name == "player_cape.jem":
				continue
			target = (RESOURCE_ROOT / relative_path).resolve()
			if not target.is_relative_to(RESOURCE_ROOT.resolve()):
				raise RuntimeError(f"Archive entry escapes resources directory: {entry.filename}")
			target.parent.mkdir(parents=True, exist_ok=True)
			target.write_bytes(archive.read(entry))
			written += 1

	# This player archive references a face model that it does not include.
	for model_name in ("player.jem", "player_slim.jem"):
		model_path = RESOURCE_ROOT / "assets/minecraft/emf/cem" / model_name
		model = json.loads(model_path.read_text(encoding="utf-8"))
		model["models"] = [part for part in model["models"] if part.get("id") != "player_face"]
		head = next(part for part in model["models"] if part.get("id") == "head")
		root_animation = next(animation for animation in head["animations"] if "root.rx" in animation)
		for axis in ("sx", "sy", "sz"):
			root_animation[f"root.{axis}"] = "1"
		for axis in ("rx", "ry", "rz", "tx", "ty", "tz"):
			root_animation[f"root.{axis}"] = "0"
		model_path.write_text(json.dumps(model, separators=(",", ":")) + "\n", encoding="utf-8")
	(RESOURCE_ROOT / "assets/minecraft/emf/cem/player_cape.jem").unlink(missing_ok=True)
	patch_player_crouch_sway()
	return written


def patch_player_crouch_sway():
	cem_root = RESOURCE_ROOT / "assets/minecraft/emf/cem"
	variables_path = cem_root / "a_player_variables.jpm"
	variables = json.loads(variables_path.read_text(encoding="utf-8"))
	variable_group = variables["animations"][0]
	variable_group.update({
		"var.crouch_sway_time": "if(varb.fcc,var.crouch_sway_time,is_sneaking&&is_on_ground&&limb_speed<0.05,min(5,var.crouch_sway_time+frame_time),0)",
		"var.crouch_sway_weight": "clamp((var.crouch_sway_time-1.5)/1.2,0,1)*var.sneak",
		"var.crouch_sway_phase": "age/7",
		"var.crouch_sway_body": "torad(7)*sin(var.crouch_sway_phase)*var.crouch_sway_weight",
		"var.crouch_sway_twist": "torad(3)*cos(var.crouch_sway_phase)*var.crouch_sway_weight",
		"var.crouch_sway_right_arm": "torad(5)*sin(var.crouch_sway_phase+pi/2)*var.crouch_sway_weight",
		"var.crouch_sway_left_arm": "-torad(5)*sin(var.crouch_sway_phase+pi/2)*var.crouch_sway_weight",
		"var.crouch_sway_right_arm_rx": "torad(12)*sin(var.crouch_sway_phase)*var.crouch_sway_weight",
		"var.crouch_sway_left_arm_rx": "torad(12)*sin(var.crouch_sway_phase+pi)*var.crouch_sway_weight",
		"var.crouch_sway_right_leg": "torad(9)*sin(var.crouch_sway_phase+pi)*var.crouch_sway_weight",
		"var.crouch_sway_left_leg": "torad(9)*sin(var.crouch_sway_phase)*var.crouch_sway_weight",
	})
	variables_path.write_text(json.dumps(variables, separators=(",", ":")) + "\n", encoding="utf-8")

	def append_expression(animation, key, expression):
		if expression not in animation[key]:
			animation[key] = f"({animation[key]})+{expression}"

	for model_name in ("player.jem", "player_slim.jem"):
		model_path = cem_root / model_name
		model = json.loads(model_path.read_text(encoding="utf-8"))
		head = next(part for part in model["models"] if part.get("id") == "head")
		animations = head["animations"]
		body_animation = next(animation for animation in animations if "body.ry" in animation and "body.rz" in animation)
		body_animation["body.ry"] = "(var.body_ry)+var.crouch_sway_twist"
		body_animation["body.rz"] = "(var.body_rz)+var.crouch_sway_body"
		arm_animation = next(animation for animation in animations if "right_arm.rz" in animation and "left_arm.rz" in animation)
		for key, expression in (
			("right_arm.rx", "var.crouch_sway_right_arm_rx"),
			("left_arm.rx", "var.crouch_sway_left_arm_rx"),
			("right_arm.rz", "var.crouch_sway_right_arm"),
			("left_arm.rz", "var.crouch_sway_left_arm"),
		):
			append_expression(arm_animation, key, expression)
		leg_animation = next(animation for animation in animations if "right_leg.rx" in animation and "left_leg.rx" in animation)
		append_expression(leg_animation, "right_leg.rx", "var.crouch_sway_right_leg")
		append_expression(leg_animation, "left_leg.rx", "var.crouch_sway_left_leg")
		layer_animation = next(animation for animation in animations if "jacket.ry" in animation and "right_sleeve.rz" in animation)
		layer_animation["jacket.ry"] = "(if(varb.21_2_plus,0,body.ry))+if(varb.21_2_plus,var.crouch_sway_twist,0)"
		layer_animation["jacket.rz"] = "(if(varb.21_2_plus,0,body.rz))+if(varb.21_2_plus,var.crouch_sway_body,0)"
		layer_animation["right_sleeve.rx"] = "(if(varb.21_2_plus,0,right_arm.rx))+if(varb.21_2_plus,var.crouch_sway_right_arm_rx,0)"
		layer_animation["left_sleeve.rx"] = "(if(varb.21_2_plus,0,left_arm.rx))+if(varb.21_2_plus,var.crouch_sway_left_arm_rx,0)"
		layer_animation["right_sleeve.rz"] = "(if(varb.21_2_plus,0,right_arm.rz))+if(varb.21_2_plus,var.crouch_sway_right_arm,0)"
		layer_animation["left_sleeve.rz"] = "(if(varb.21_2_plus,0,left_arm.rz))+if(varb.21_2_plus,var.crouch_sway_left_arm,0)"
		layer_animation["right_pants.rx"] = "(if(varb.21_2_plus,0,right_leg.rx))+if(varb.21_2_plus,var.crouch_sway_right_leg,0)"
		layer_animation["left_pants.rx"] = "(if(varb.21_2_plus,0,left_leg.rx))+if(varb.21_2_plus,var.crouch_sway_left_leg,0)"
		model_path.write_text(json.dumps(model, separators=(",", ":")) + "\n", encoding="utf-8")


def remove_legacy_baby_animal_overrides():
	cem_root = RESOURCE_ROOT / "assets/minecraft/optifine/cem"
	for model_name in OUTDATED_BABY_MODELS:
		(cem_root / model_name).unlink(missing_ok=True)
	for texture_name in OUTDATED_BABY_TEXTURES:
		(RESOURCE_ROOT / "assets/minecraft/textures/entity" / texture_name).unlink(missing_ok=True)


def patch_pillager_voice_animation():
	model_path = RESOURCE_ROOT / "assets/minecraft/optifine/cem/pillager.jem"
	animation_path = RESOURCE_ROOT / "assets/minecraft/optifine/cem/pillager_animations.jpm"
	model = json.loads(model_path.read_text(encoding="utf-8"))
	root_model = next(item for item in model["models"] if item.get("part") == "root")
	if root_model.get("model") != "pillager_animations.jpm":
		raise RuntimeError("Imported Fresh Animations pillager model is missing its animation file reference")

	animations = json.loads(animation_path.read_text(encoding="utf-8"))["animations"]
	mouth_animation = next(item for item in animations if "mouth.sy" in item)
	mouth_expression = mouth_animation["mouth.sy"]
	if "vnap_speaking" not in mouth_expression:
		mouth_animation["mouth.sy"] = (
			f"(1-vnap_speaking)*({mouth_expression})"
			"+vnap_speaking*(0.15+1.45*vnap_mouth_open-0.1*vnap_mouth_closed)"
		)
	def set_mouth_texture(part):
		if isinstance(part, dict):
			if part.get("id") == "mouth":
				part["texture"] = "textures/entity/illager/pillager_mouth.png"
				part["textureSize"] = [16, 16]
				for box in part.get("boxes", []):
					box.pop("textureOffset", None)
					box["uvNorth"] = [0, 0, 4, 2]
			for value in part.values():
				set_mouth_texture(value)
		elif isinstance(part, list):
			for value in part:
				set_mouth_texture(value)
	set_mouth_texture(model)

	pose_animation = next(item for item in animations if "head.rx" in item and "body.rx" in item)
	for part, amount in (("head", "0.22"), ("body", "0.12")):
		for axis in ("rx", "ry", "rz"):
			key = f"{part}.{axis}"
			addition = f"+vnap_speaking*vnap_{part}_{axis}*{amount}"
			if addition not in pose_animation[key]:
				pose_animation[key] += addition

	animation_path.write_text(json.dumps({"animations": animations}, indent="\t") + "\n", encoding="utf-8")
	model_path.write_text(json.dumps(model, indent="\t") + "\n", encoding="utf-8")


def main():
	written = 0
	remove_competing_emf_villager_models()
	for archive_name in ARCHIVES:
		archive_path = ARCHIVE_ROOT / archive_name
		if not archive_path.is_file():
			raise FileNotFoundError(f"Missing supplied animation archive: {archive_path}")
		with zipfile.ZipFile(archive_path) as archive:
			entries = sorted(archive.infolist(), key=lambda item: (item.filename.count("/"), item.filename))
			for entry in entries:
				if entry.is_dir() or entry.filename.lower().endswith(("pack.mcmeta", "pack.png", "terms&conditions.txt")):
					continue
				if "changelog" in entry.filename.lower():
					continue
				relative_name = asset_path(entry.filename)
				if relative_name is None:
					continue
				relative_path = Path(*relative_name.split("/"))
				if is_conflicting_villager_asset(relative_path):
					continue
				if is_conflicting_player_asset(relative_path):
					continue
				target = (RESOURCE_ROOT / relative_path).resolve()
				if not target.is_relative_to(RESOURCE_ROOT.resolve()):
					raise RuntimeError(f"Archive entry escapes resources directory: {entry.filename}")
				if relative_path.parts[:4] == ("assets", "minecraft", "optifine", "cem"):
					model_name = relative_path.name
					if model_name in PRESERVE_CUSTOM_MODELS:
						continue
				target.parent.mkdir(parents=True, exist_ok=True)
				target.write_bytes(archive.read(entry))
				written += 1

	written += import_baby_animal_models()
	written += import_player_animation_models()
	patch_pillager_voice_animation()
	remove_legacy_baby_animal_overrides()
	print(f"Imported {written} Fresh Animations and extension assets for private testing.")
	print("Original Villager News villager models are preserved; Fresh Animations player models and animations are included.")


if __name__ == "__main__":
	main()
