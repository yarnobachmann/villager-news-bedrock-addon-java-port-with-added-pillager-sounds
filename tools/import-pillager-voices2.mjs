import { mkdirSync, readdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { basename, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(join(fileURLToPath(new URL(".", import.meta.url)), ".."));
const sourceDir = join(root, "voices2");
const assetsDir = join(root, "src/main/resources/assets/villager-news-addon-port");
const audioDir = join(assetsDir, "sounds/pillager");
const dialoguePath = join(assetsDir, "pillager_dialogues.json");
const soundsPath = join(assetsDir, "sounds.json");
const animationsPath = join(assetsDir, "dialogue_animations.json");
const languagePath = join(assetsDir, "lang/en_us.json");
const preparedDir = join(root, ".pillager-voice-import");

const normalize = value => value.toLocaleLowerCase("en-US").replace(/[^a-z0-9]/g, "");
const classify = name => {
	const text = normalize(name);
	if (/wave|menacing|signal|lookbusy|greatnowthewhole|stopwaving|heswaving/.test(text)) return "group_gag";
	if (/golem|emergencyplan/.test(text)) return "golem";
	if (/raid|hornsounded|villageahead|planchanges/.test(text)) return "raid";
	if (/onearrow|arrowmanagement|thevillagersbeenhit|theysaidohnoarrows|thearrowworked/.test(text)) return "villager_hit";
	if (/targetisfleeing|theyregettingaway|theyrerunning|whyaretheyrunning|runvillager|dontletthemescape/.test(text)) return "villager_flee";
	if (/playerattackedme|greetapatrol|uniformisnotprotective|ivebeenhit|ouchie|hitmyarmour|youhitmyarmour/.test(text)) return "pillager_hurt";
	if (/weirdlookingvillager|dontthinkthatsavillager|suspiciousnumberofelbows/.test(text)) return "player_sighting";
	if (/villager|craftingtable/.test(text)) return "villager_sighting";
	if (/sword|bow|hoe|armour|armor|shield|receipt/.test(text)) return "player_gear";
	if (/holdingfood|askforabite/.test(text)) return "player_food";
	if (/staring|watchingus|observing|scenery/.test(text)) return "player_stare";
	if (/arrow|bullseye|directhit|targethit|excellentshot/.test(text)) return "shot_hit";
	if (/player|boat|mineus|elbows|pumpkin/.test(text)) return "player_sighting";
	if (/backup|patrol|formation|route|map|quiet|bush|keepup|followingme|lookdangerous|company|escape|move|official/.test(text)) return "patrol";
	return "ambient";
};
const gestureFor = pool => ({
	group_gag: 0,
	player_gear: 11,
	player_food: 12,
	player_stare: 13,
	player_sighting: 11,
	villager_hit: 18,
	villager_flee: 23,
	villager_sighting: 23,
	golem: 13,
	pillager_hurt: 18,
	shot_hit: 18,
	raid: 11,
	patrol: 0,
	ambient: 9,
	egg_hatch: 12,
})[pool] ?? 9;

const buildMouthTimeline = audioPath => {
	const sampleRate = 8000;
	const frameSamples = 640;
	const pcm = readFileSync(audioPath);
	const samples = new Int16Array(pcm.buffer, pcm.byteOffset, Math.floor(pcm.byteLength / 2));
	const energy = [];
	for (let start = 0; start < samples.length; start += frameSamples) {
		let sum = 0;
		const end = Math.min(samples.length, start + frameSamples);
		for (let index = start; index < end; index++) sum += (samples[index] / 32768) ** 2;
		energy.push(Math.sqrt(sum / Math.max(1, end - start)));
	}
	const sorted = [...energy].sort((a, b) => a - b);
	const percentile = value => sorted[Math.min(sorted.length - 1, Math.floor((sorted.length - 1) * value))] ?? 0;
	const noise = Math.max(percentile(0.18) * 1.25, percentile(0.95) * 0.06);
	const peak = Math.max(noise + 0.001, percentile(0.95));
	const mouth = [[0, 0, 1, 1]];
	for (let index = 0; index < energy.length; index++) {
		const smooth = (energy[Math.max(0, index - 1)] + 2 * energy[index] + energy[Math.min(energy.length - 1, index + 1)]) / 4;
		const normalized = Math.max(0, Math.min(1, (smooth - noise) / (peak - noise)));
		const open = normalized < 0.12 ? 0 : (normalized - 0.12) / 0.88;
		mouth.push([Number((index * frameSamples / sampleRate).toFixed(3)), Number(open.toFixed(3)), Number((0.7 + open * 0.3).toFixed(3)), open < 0.12 ? 1 : 0]);
	}
	return mouth;
};

const hatchDir = join(root, "Egg hatch sound pillager");
const sourceFiles = [
	...readdirSync(sourceDir).filter(name => name.toLowerCase().endsWith(".ogg")).sort((a, b) => a.localeCompare(b, "en")).map(name => ({ name, directory: sourceDir })),
	...readdirSync(hatchDir).filter(name => name.toLowerCase().endsWith(".ogg")).sort((a, b) => a.localeCompare(b, "en")).map(name => ({ name, directory: hatchDir })),
];
if (sourceFiles.length === 0) throw new Error(`No OGG recordings found in ${sourceDir}`);
const catalog = JSON.parse(readFileSync(dialoguePath, "utf8"));
const sounds = JSON.parse(readFileSync(soundsPath, "utf8"));
const animations = JSON.parse(readFileSync(animationsPath, "utf8"));
const language = JSON.parse(readFileSync(languagePath, "utf8"));
mkdirSync(audioDir, { recursive: true });

const waveGesture = {
duration: 9,
tracks: (() => {
	const leftArmRx = [];
	const leftArmRy = [];
	const leftArmRz = [];
	for (let frame = 0; frame <= 9 * 24; frame++) {
		const time = frame / 24;
		const lift = Math.min(1, time / 0.55, (9 - time) / 0.7);
		const wave = Math.sin((time - 0.55) * Math.PI * 2 * 0.8);
		leftArmRx.push(-0.35 * Math.max(0, lift));
		leftArmRy.push(time < 0.55 || time > 8.3 ? 0 : wave * 0.45);
		leftArmRz.push(2.55 * Math.max(0, lift));
	}
	return { left_arm_rx: leftArmRx, left_arm_ry: leftArmRy, left_arm_rz: leftArmRz };
})(),
};
const pointGesture = {
	duration: 1.8,
	tracks: {
		arms_rx: [0.0, -0.35, -0.75, -0.75, -0.75, -0.35, 0.0],
		arms_ry: [0.0, 0.15, 0.3, 0.3, 0.3, 0.15, 0.0],
		arms_rz: [0.0, -0.1, -0.18, -0.18, -0.18, -0.1, 0.0],
		head_ry: [0.0, 0.15, 0.18, 0.18, 0.18, 0.1, 0.0],
	},
};
animations.gestures[46] ??= pointGesture;
animations.gestures[47] = waveGesture;
const groups = catalog.groups;
const newGroups = {};

for (const [offset, source] of sourceFiles.entries()) {
	const { name: audioName, directory } = source;
	const index = offset + 49;
	const id = `pillager_${String(index).padStart(3, "0")}`;
	const transcript = basename(audioName, ".ogg").replace(/[“”]/g, '"').replace(/[‘’]/g, "'").trim();
	const pool = directory === hatchDir ? "egg_hatch" : classify(transcript);
	const normalizedTranscript = normalize(transcript);
	const gestureIndex = pool === "group_gag"
	? /wave/.test(normalizedTranscript) ? 46 : 0
		: gestureFor(pool);
	const filename = `line_${String(index).padStart(3, "0")}.ogg`;
	const outputAudio = join(audioDir, filename);
	const temporaryAudio = `${outputAudio}.mono.ogg`;
	execFileSync("ffmpeg", ["-v", "error", "-y", "-i", join(directory, audioName), "-map_metadata", "-1", "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", temporaryAudio]);
	renameSync(temporaryAudio, outputAudio);
	const preparedAudio = join(preparedDir, filename.replace(/\.ogg$/i, ".pcm"));
	const duration = Number(readFileSync(join(preparedDir, filename.replace(/\.ogg$/i, ".duration")), "utf8").trim());
	const subtitleKey = `subtitles.villager-news-addon-port.dialogue.${id}.0.0`;
	const mouth = buildMouthTimeline(preparedAudio);
	mouth.push([Number(duration.toFixed(3)), 0, 1, 1]);
	const title = `Pillager ${String(index).padStart(3, "0")}: ${pool.replaceAll("_", " ")}`;
	newGroups[id] = {
		title,
		body: `Pillager dialogue. Event pool: ${pool}.`,
		speaker: "pillager",
		maximumDuration: Number(duration.toFixed(3)),
		variants: [{ index: 0, duration: Number(duration.toFixed(3)), weight: 1,
			animation: `animation.vnap.pillager.${String(index).padStart(3, "0")}`,
			subtitles: [{ time: 0, key: subtitleKey }] }],
		recording: audioName,
		pool,
	};
	sounds[`dialogue.${id}.0`] = { attenuation_distance: 24, sounds: [{ name: `villager-news-addon-port:pillager/${filename.slice(0, -4)}`, stream: true }] };
	animations.groups[id] = [{ mouth, gestures: [[0, gestureIndex]] }];
	language[subtitleKey] = transcript;
}

groups.pillager_wave_only = {
	title: "Pillager reaction: wave",
	body: "Silent group wave animation.",
	speaker: "pillager",
	maximumDuration: 9,
	variants: [{ index: 0, duration: 9, weight: 1, animation: "animation.vnap.pillager.wave", subtitles: [] }],
	pool: "wave_only",
};
animations.groups.pillager_wave_only = [{ mouth: [], gestures: [[0, 47]] }];

Object.assign(groups, newGroups);
writeFileSync(dialoguePath, `${JSON.stringify({ groups }, null, 2)}\n`);
writeFileSync(soundsPath, `${JSON.stringify(sounds, null, 2)}\n`);
writeFileSync(animationsPath, `${JSON.stringify(animations, null, 2)}\n`);
writeFileSync(languagePath, `${JSON.stringify(language, null, 2)}\n`);
console.log(`Imported ${sourceFiles.length} recordings into pillager_049–pillager_${String(sourceFiles.length + 48).padStart(3, "0")}.`);
for (const pool of [...new Set(Object.values(newGroups).map(group => group.pool))]) {
	console.log(`${pool}: ${Object.entries(newGroups).filter(([, group]) => group.pool === pool).map(([id]) => id).join(", ")}`);
}
