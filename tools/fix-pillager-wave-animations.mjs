import { readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(join(fileURLToPath(new URL(".", import.meta.url)), ".."));
const assetsDir = join(root, "src/main/resources/assets/villager-news-addon-port");
const modelAnimationsPath = join(root, "src/main/resources/assets/minecraft/optifine/cem/pillager_animations.jpm");
const dialogueAnimationsPath = join(assetsDir, "dialogue_animations.json");
const dialoguesPath = join(assetsDir, "pillager_dialogues.json");
const soundsPath = join(assetsDir, "sounds.json");

const modelAnimations = JSON.parse(readFileSync(modelAnimationsPath, "utf8"));
const bodyAnimation = modelAnimations.animations.find(animation => animation["right_arm.rx"] && animation["left_arm.rx"]);
if (!bodyAnimation) throw new Error("Could not find the pillager arm animation block.");

const tracks = [
	["right_arm.rx", "+vnap_speaking*vnap_arms_rx"],
	["left_arm.rx", "+vnap_speaking*vnap_arms_rx"],
	["right_arm.ry", "+vnap_speaking*vnap_arms_ry"],
	["left_arm.ry", "-vnap_speaking*vnap_arms_ry"],
	["right_arm.rz", "+vnap_speaking*vnap_arms_rz"],
	["left_arm.rz", "-vnap_speaking*vnap_arms_rz"],
	["left_arm.rx", "+vnap_left_arm_rx"],
	["left_arm.ry", "+vnap_left_arm_ry"],
	["left_arm.rz", "+vnap_left_arm_rz"],
];

const makeWaveGesture = () => {
	const frames = 9 * 24 + 1;
	const leftArmRx = [];
	const leftArmRy = [];
	const leftArmRz = [];
	for (let frame = 0; frame < frames; frame++) {
		const time = frame / 24;
		const lift = Math.min(1, time / 0.55, (9 - time) / 0.7);
		const wave = Math.sin((time - 0.55) * Math.PI * 2 * 0.8);
		leftArmRx.push(-0.35 * Math.max(0, lift));
		leftArmRy.push(time < 0.55 || time > 8.3 ? 0 : wave * 0.45);
		leftArmRz.push(2.55 * Math.max(0, lift));
	}
	return { duration: 9, tracks: { left_arm_rx: leftArmRx, left_arm_ry: leftArmRy, left_arm_rz: leftArmRz } };
};

for (const [part, expression] of tracks) {
	if (part.startsWith("left_arm.")) {
		bodyAnimation[part] = bodyAnimation[part]
			.replaceAll("+vnap_speaking*vnap_left_arm_", "+vnap_left_arm_")
			.replaceAll("+vnap_gesture_active*vnap_left_arm_", "+vnap_left_arm_");
	}
	if (!bodyAnimation[part].includes(expression)) bodyAnimation[part] += expression;
}
writeFileSync(modelAnimationsPath, `${JSON.stringify(modelAnimations, null, 2)}\n`);

const dialogueAnimations = JSON.parse(readFileSync(dialogueAnimationsPath, "utf8"));
dialogueAnimations.gestures[47] = makeWaveGesture();
const gestures = {
	56: 0,
	62: 0,
	63: 46,
	68: 46,
	69: 46,
	71: 46,
	82: 0,
	84: 46,
	98: 46,
};
for (const [line, gesture] of Object.entries(gestures)) {
	const id = `pillager_${String(line).padStart(3, "0")}`;
	if (dialogueAnimations.groups[id]?.[0]) dialogueAnimations.groups[id][0].gestures = [[0, gesture]];
}
writeFileSync(dialogueAnimationsPath, `${JSON.stringify(dialogueAnimations, null, 2)}\n`);
const dialogues = JSON.parse(readFileSync(dialoguesPath, "utf8"));
dialogues.groups.pillager_wave_only.maximumDuration = 9;
dialogues.groups.pillager_wave_only.variants[0].duration = 9;
writeFileSync(dialoguesPath, `${JSON.stringify(dialogues, null, 2)}\n`);
const sounds = JSON.parse(readFileSync(soundsPath, "utf8"));
sounds["dialogue.pillager_wave_only.0"] = {
	sounds: [{ name: "villager-news-addon-port:silence", stream: false }],
};
writeFileSync(soundsPath, `${JSON.stringify(sounds, null, 2)}\n`);
console.log("Connected pillager dialogue gestures to both arm rotations and corrected wave/point gestures.");
