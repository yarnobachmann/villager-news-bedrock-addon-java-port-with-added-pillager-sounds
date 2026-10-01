import { mkdirSync, readdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { basename, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(join(fileURLToPath(new URL(".", import.meta.url)), ".."));
const sourceDir = join(root, "vexSounds");
const assetsDir = join(root, "src/main/resources/assets/villager-news-addon-port");
const audioDir = join(assetsDir, "sounds/vex");
const dialoguePath = join(assetsDir, "dialogues.json");
const soundsPath = join(assetsDir, "sounds.json");
const animationsPath = join(assetsDir, "dialogue_animations.json");
const languagePath = join(assetsDir, "lang/en_us.json");

const recordings = [
	{ id: "vex_001", file: "I am going to stab you I am going to stab you Hah I am not there yet.ogg", duration: 7.957333 },
	{ id: "vex_002", file: "Now you see me Now you dont.ogg", duration: 3.84 },
	{ id: "vex_003", file: "Where are you creature There you are Nyahaha.ogg", duration: 5.679271 },
	{ id: "vex_004", file: "Woooo Have you seen that weird creature The one with the shiny armor I wanna steal it.ogg", duration: 6.574354 },
	{ id: "vex_005", file: "Woooo We coming from the dead To punch you Oh way we have a sword To slash you.ogg", duration: 8.767708 },
];

const groups = JSON.parse(readFileSync(dialoguePath, "utf8")).groups;
const sounds = JSON.parse(readFileSync(soundsPath, "utf8"));
const animations = JSON.parse(readFileSync(animationsPath, "utf8"));
const language = JSON.parse(readFileSync(languagePath, "utf8"));
mkdirSync(audioDir, { recursive: true });

for (const [index, recording] of recordings.entries()) {
	if (!readdirSync(sourceDir).includes(recording.file)) throw new Error(`Missing recording: ${recording.file}`);
	const number = String(index + 1).padStart(3, "0");
	const audioName = `line_${number}.ogg`;
	const transcript = basename(recording.file, ".ogg").trim();
	const subtitleKey = `subtitles.villager-news-addon-port.dialogue.${recording.id}.0.0`;
	const convertedAudio = join(audioDir, `.line_${number}.mono.ogg`);
	execFileSync("ffmpeg", ["-v", "error", "-y", "-i", join(sourceDir, recording.file),
		"-map_metadata", "-1", "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", convertedAudio], { stdio: "inherit" });
	renameSync(convertedAudio, join(audioDir, audioName));
	groups[recording.id] = {
		title: `Vex ${number}: ${transcript}`,
		body: `Vex dialogue. Triggered by its arrival, target selection, or attack. Recording: ${recording.file}`,
		speaker: "vex",
		maximumDuration: recording.duration,
		variants: [{ index: 0, duration: recording.duration, weight: 1,
			animation: `animation.vnap.vex.${number}`,
			subtitles: [{ time: 0, key: subtitleKey }] }],
	};
	sounds[`dialogue.${recording.id}.0`] = {
		attenuation_distance: 24,
		sounds: [{ name: `villager-news-addon-port:vex/${audioName.slice(0, -4)}`, stream: true }],
	};
	animations.groups[recording.id] = [{ mouth: [], gestures: [] }];
	language[subtitleKey] = transcript;
}

writeFileSync(dialoguePath, `${JSON.stringify({ groups }, null, 2)}\n`);
writeFileSync(soundsPath, `${JSON.stringify(sounds, null, 2)}\n`);
writeFileSync(animationsPath, `${JSON.stringify(animations, null, 2)}\n`);
writeFileSync(languagePath, `${JSON.stringify(language, null, 2)}\n`);
console.log(`Imported ${recordings.length} Vex lines with sound events and subtitle entries.`);
