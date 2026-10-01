import { mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { basename, join, resolve } from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = resolve(join(fileURLToPath(new URL(".", import.meta.url)), ".."));
const sourceDir = join(root, "voices");
const assetsDir = join(root, "src/main/resources/assets/villager-news-addon-port");
const audioDir = join(assetsDir, "sounds/pillager");
const dialoguePath = join(assetsDir, "pillager_dialogues.json");
const soundsPath = join(assetsDir, "sounds.json");
const animationsPath = join(assetsDir, "dialogue_animations.json");

const lines = [
	["player_approach", "Halt. This is an official patrol. We look very official.", "Confident", "Player approaches"],
	["player_approach_nervous", "You’re walking toward me. Was that in the briefing?", "Nervous", "Player approaches"],
	["player_stares", "You can stop observing the patrol now.", "Confident", "Player watches the pillager"],
	["player_armored", "That is a lot of armor for someone who says hello.", "Nervous", "Nearby player wears armor"],
	["player_weapon", "Excellent weapon. Please keep it pointed somewhere else.", "Confident", "Nearby player holds a weapon"],
	["player_ominous_item", "I don’t know what that is, and I’d like to keep it that way.", "Nervous", "Nearby player holds an unfamiliar item"],
	["player_leaves", "Good. The patrol has successfully moved you along.", "Confident", "Player leaves the patrol"],
	["player_returns", "You came back. I was hoping that was a different person.", "Nervous", "Player returns shortly after leaving"],
	["villager_spotted", "Villager spotted. They appear to have no idea what’s going on.", "Confident", "Pillager spots a villager"],
	["villager_stares", "Why is it looking at me like that?", "Nervous", "Villager watches the pillager"],
	["villager_nearby", "Keep moving, citizen. This is a very important patrol.", "Confident", "Villager walks nearby"],
	["villager_nose", "That nose is closer than it looked from over there.", "Nervous", "Villager stands close"],
	["villager_crowd", "A crowd. Stay calm and look like we planned this.", "Confident", "Pillager sees several villagers"],
	["villagers_gossip", "They’re talking about us. I can tell because they have noses.", "Nervous", "Villagers gather nearby"],
	["villager_runs", "The citizen has chosen the fast exit.", "Confident", "Villager runs away"],
	["villager_pockets", "I don’t know what it wants. It has no visible pockets.", "Nervous", "Villager approaches"],
	["golem_spotted", "There’s the village security department.", "Confident", "Pillager spots an iron golem"],
	["golem_notices", "It has noticed us. Everybody act like a flower.", "Nervous", "Iron golem looks toward the pillager"],
	["golem_nearby", "We’re just passing through. Very slowly. In the other direction.", "Confident", "Iron golem is close"],
	["golem_approaches", "That one has arms like a whole patrol.", "Nervous", "Iron golem approaches"],
	["golem_leaves", "The situation is under control. It left first.", "Confident", "Iron golem walks away"],
	["golem_guards_villagers", "It’s guarding them. That seems excessive and sensible.", "Nervous", "Iron golem stands near villagers"],
	["golem_damaged", "The village security department has seen better days.", "Confident", "Pillager sees a damaged iron golem"],
	["golem_patrol_route", "I’m adding this route to the map under ‘absolutely not.’", "Nervous", "Pillager plans a patrol route near a golem"],
	["pillager_backup", "Good, backup. Now we can be outnumbered together.", "Confident", "Pillager meets another pillager"],
	["pillager_late", "You’re late. I’ve been nervous by myself for ages.", "Nervous", "Another pillager joins the patrol"],
	["pillager_formation", "We are moving in formation. The formation is accidental.", "Confident", "Pillagers walk together"],
	["pillager_lost", "I know where they went. I just don’t know where I am.", "Nervous", "Pillager loses sight of its group"],
	["raid_party", "Everyone look prepared. We’ll start with the ones who are.", "Confident", "Pillager sees a raid party"],
	["raid_horn", "That horn means something. I hope it means lunch.", "Nervous", "Raid horn sounds nearby"],
	["raid_village", "The village is ahead. Let’s all remember the plan.", "Confident", "Pillager approaches a village during a raid"],
	["raid_plan", "The plan has changed. I was not told the new plan.", "Nervous", "Raid is underway"],
	["hurt_armor", "I’m fine. That was my armor complaining.", "Nervous", "Pillager takes damage"],
	["hurt_confident", "A minor setback. My confidence is still intact.", "Confident", "Pillager takes damage"],
	["player_attacks", "That is not how you greet a patrol!", "Nervous", "Player attacks the pillager"],
	["aims_crossbow", "Stand still while I make a very careful decision.", "Confident", "Pillager aims its crossbow"],
	["missed_shot", "That shot was a warning. To the wall.", "Nervous", "Pillager misses a shot"],
	["reloads_crossbow", "Reloading. Please remain impressed.", "Confident", "Pillager reloads its crossbow"],
	["nearby_fight", "I’m going to help. From a safe distance.", "Nervous", "Pillager sees a nearby fight"],
	["fight_ends", "The area is secure. I had very little to do with it.", "Confident", "A nearby fight ends"],
	["ambient_patrol", "Patrol report: still patrolling.", "Confident", "Rare ambient comment"],
	["ambient_route", "I was told there would be a route.", "Nervous", "Rare ambient comment"],
	["ambient_crossbow", "This crossbow is heavier when nobody’s watching.", "Confident", "Rare ambient comment"],
	["ambient_guard", "If anyone asks, I was standing guard.", "Nervous", "Rare ambient comment"],
	["ambient_map", "I know where I’m going. The map is simply mistaken.", "Confident", "Rare ambient comment while wandering"],
	["ambient_quiet", "It’s quiet here. Quiet usually means something.", "Nervous", "Rare ambient comment near a village"],
	["ambient_bell", "That bell is far too good at getting everyone’s attention.", "Confident", "Rare ambient comment near a bell"],
	["ambient_not_lost", "I’m not lost. I’m checking the surroundings from this spot.", "Nervous", "Rare ambient comment while idle"],
];

const normalize = (value) => value.toLocaleLowerCase("en-US").replace(/[“”‘’]/g, "").replace(/[^a-z0-9]/g, "");
const buildMouthTimeline = (audioPath) => {
	const sampleRate = 8000;
	const frameSamples = 640;
	const pcm = execFileSync("ffmpeg", [
		"-hide_banner", "-loglevel", "error", "-i", audioPath, "-af", "aresample=8000,asetpts=N/SR/TB",
		"-f", "s16le", "-acodec", "pcm_s16le", "-ac", "1", "-ar", String(sampleRate), "-",
	]);
	const samples = new Int16Array(pcm.buffer, pcm.byteOffset, Math.floor(pcm.byteLength / 2));
	const energies = [];
	for (let start = 0; start < samples.length; start += frameSamples) {
		const end = Math.min(samples.length, start + frameSamples);
		let sum = 0;
		for (let index = start; index < end; index++) sum += (samples[index] / 32768) ** 2;
		energies.push(Math.sqrt(sum / Math.max(1, end - start)));
	}
	const sorted = [...energies].sort((a, b) => a - b);
	const percentile = (value) => sorted[Math.min(sorted.length - 1, Math.floor((sorted.length - 1) * value))] ?? 0;
	const noise = Math.max(percentile(0.18) * 1.25, percentile(0.95) * 0.06);
	const peak = Math.max(noise + 0.001, percentile(0.95));
	const mouth = [[0, 0, 1, 1]];
	for (let index = 0; index < energies.length; index++) {
		const smooth = (energies[Math.max(0, index - 1)] + 2 * energies[index] + energies[Math.min(energies.length - 1, index + 1)]) / 4;
		const normalized = Math.max(0, Math.min(1, (smooth - noise) / (peak - noise)));
		const open = normalized < 0.12 ? 0 : (normalized - 0.12) / 0.88;
		const time = Number((index * frameSamples / sampleRate).toFixed(3));
		mouth.push([time, Number(open.toFixed(3)), Number((0.7 + open * 0.3).toFixed(3)), open < 0.12 ? 1 : 0]);
	}
	return mouth;
};
const sourceFiles = readdirSync(sourceDir).filter((name) => name.toLowerCase().endsWith(".ogg"));
const byNormalizedName = new Map(sourceFiles.map((name) => [normalize(name.replace(/\.ogg$/i, "")), name]));
const sounds = JSON.parse(readFileSync(soundsPath, "utf8"));
const animations = JSON.parse(readFileSync(animationsPath, "utf8"));
const groups = {};
mkdirSync(audioDir, { recursive: true });

for (const [offset, [trigger, transcript, delivery, condition]] of lines.entries()) {
	const index = offset + 1;
	const id = `pillager_${String(index).padStart(3, "0")}`;
	const audioName = byNormalizedName.get(normalize(transcript));
	if (!audioName && trigger !== "golem_approaches") throw new Error(`Missing recording for: ${transcript}`);
	const filename = `line_${String(index).padStart(3, "0")}.ogg`;
	execFileSync("ffmpeg", [
		"-hide_banner", "-loglevel", "error", "-y", "-i",
		join(sourceDir, audioName ?? "hat one has arms like a whole patrol.ogg"),
		"-vn", "-ac", "1", "-ar", "48000", "-c:a", "libvorbis", "-q:a", "5",
		join(audioDir, filename),
	]);
	const duration = Number(execFileSync("ffprobe", [
		"-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1",
		join(audioDir, filename),
	], { encoding: "utf8" }).trim());
	const title = `Pillager ${String(index).padStart(2, "0")}: ${condition}`;
	const subtitleKey = `subtitles.villager-news-addon-port.dialogue.${id}.0.0`;
	const mouth = buildMouthTimeline(join(audioDir, filename));
	mouth.push([Number(duration.toFixed(3)), 0, 1, 1]);
	const gestureIndex = (index * 7 + (delivery === "Nervous" ? 13 : 3)) % animations.gestures.length;
	groups[id] = {
		title,
		body: `Pillager reaction. Delivery: ${delivery}. Trigger: ${condition}.`,
		speaker: "pillager",
		maximumDuration: Number(duration.toFixed(3)),
		variants: [{ index: 0, duration: Number(duration.toFixed(3)), weight: 1,
			animation: `animation.vnap.pillager.${String(index).padStart(3, "0")}`,
			subtitles: [{ time: 0, key: subtitleKey }] }],
	};
	sounds[`dialogue.${id}.0`] = { attenuation_distance: 24, sounds: [{ name: `villager-news-addon-port:pillager/${filename.replace(/\.ogg$/i, "")}`, stream: true }] };
	animations.groups[id] = [{ mouth, gestures: [[0, gestureIndex]] }];
	// Keep source spelling and timing in the catalog so line edits can be checked against each take.
	groups[id].recording = audioName;
	groups[id].trigger = trigger;
	groups[id].delivery = delivery;
	groups[id].variants[0].duration = Number(duration.toFixed(3));
	const languagePath = join(assetsDir, "lang/en_us.json");
	const language = JSON.parse(readFileSync(languagePath, "utf8"));
	language[subtitleKey] = transcript;
	writeFileSync(languagePath, `${JSON.stringify(language, null, 2)}\n`);
}

writeFileSync(dialoguePath, `${JSON.stringify({ groups }, null, 2)}\n`);
writeFileSync(soundsPath, `${JSON.stringify(sounds, null, 2)}\n`);
writeFileSync(animationsPath, `${JSON.stringify(animations, null, 2)}\n`);
console.log(`Imported ${lines.length} pillager recordings from ${basename(sourceDir)}.`);
