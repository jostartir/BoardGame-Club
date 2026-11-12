"use strict";

const stripeColors = ["#2a4fd6", "#12806f", "#b0368c", "#7a4fd1", "#c27a00", "#0f7fb0"];

const el = {
    catalog: document.getElementById("catalog"),
    catalogNote: document.getElementById("catalog-note"),
    message: document.getElementById("message"),
    meetingForm: document.getElementById("meeting-form"),
    title: document.getElementById("title"),
    when: document.getElementById("when"),
    meetings: document.getElementById("meetings"),
    meetingsEmpty: document.getElementById("meetings-empty"),
    minutes: document.getElementById("minutes"),
    meetingInfo: document.getElementById("meeting-info"),
    participants: document.getElementById("participants"),
    participantsEmpty: document.getElementById("participants-empty"),
    participantForm: document.getElementById("participant-form"),
    name: document.getElementById("name"),
    addButton: document.getElementById("add-button"),
    strategies: document.getElementById("strategies"),
    recommendNote: document.getElementById("recommend-note"),
    recommendations: document.getElementById("recommendations"),
    startButton: document.getElementById("start-button"),
    play: document.getElementById("play"),
};

let catalog = [];
let state = null;
let selectedGameId = null;
const reportOptions = { duration: true, scores: true };
let correcting = false;
let drafts = {};
let minutesDraft = null;

/* ---------- talking to the Java server ---------- */

async function api(method, path, form) {
    const options = { method };

    if (form) {
        options.headers = { "Content-Type": "application/x-www-form-urlencoded" };
        options.body = new URLSearchParams(form).toString();
    }

    let response;

    try {
        response = await fetch(path, options);
    } catch (error) {
        throw new Error("Cannot reach the server. Check that the application is still running.");
    }

    const data = await response.json();

    if (!response.ok) {
        throw new Error(data.error || "The request failed");
    }

    return data;
}

async function run(request, successMessage) {
    try {
        state = await request();
        render();

        if (successMessage) {
            showMessage(successMessage(state), "ok");
        }
    } catch (error) {
        showMessage(error.message, "error");
    }
}

function showMessage(text, kind) {
    el.message.textContent = text;
    el.message.className = "message " + kind;
}

/* ---------- small helpers ---------- */

function create(tag, className, text) {
    const node = document.createElement(tag);

    if (className) {
        node.className = className;
    }

    if (text !== undefined) {
        node.textContent = text;
    }

    return node;
}

function stripeFor(genre) {
    let hash = 0;

    for (const ch of genre) {
        hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
    }

    return stripeColors[hash % stripeColors.length];
}

function formatDate(iso) {
    return iso.slice(0, 16).replace("T", " ");
}

function setDefaultDate() {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    el.when.value = now.toISOString().slice(0, 16);
}

function resetPlayState() {
    correcting = false;
    drafts = {};
    minutesDraft = null;
    selectedGameId = null;
}

function scaleSize() {
    return Math.max(8, ...catalog.map((game) => game.maxPlayers));
}

function gameCard(game, playerCount) {
    const card = create("div", "game");
    card.style.setProperty("--stripe", stripeFor(game.genre));

    card.append(create("h4", null, game.title), create("div", "genre", game.genre));

    const facts = create("div", "facts");
    facts.append(create("span", null, game.durationMinutes + " min"));

    const difficulty = create("span", null, "Difficulty ");
    const pips = create("span", "pips");
    pips.setAttribute("aria-label", game.difficulty + " out of 5");

    for (let i = 1; i <= 5; i++) {
        pips.append(create("i", i <= game.difficulty ? "on" : ""));
    }

    difficulty.append(pips);
    facts.append(difficulty);
    card.append(facts);

    const scale = create("div", "scale");
    scale.setAttribute("aria-hidden", "true");

    for (let n = 1; n <= scaleSize(); n++) {
        const cell = create("span", null, String(n));

        if (n >= game.minPlayers && n <= game.maxPlayers) {
            cell.classList.add("on");
        }

        if (n === playerCount) {
            cell.classList.add("now");
        }

        scale.append(cell);
    }

    const range = game.minPlayers === game.maxPlayers
        ? game.minPlayers + " players only"
        : game.minPlayers + " to " + game.maxPlayers + " players";

    card.append(scale, create("p", "scale-caption", range));
    return card;
}

/* ---------- drawing the page ---------- */

function renderCatalog() {
    el.catalog.replaceChildren();

    for (const game of catalog) {
        const item = create("li");
        item.append(gameCard(game, null));
        el.catalog.append(item);
    }

    el.catalogNote.textContent = catalog.length + " games loaded from games.csv";
}

function meetingState(item) {
    if (item.game === null) {
        return "Planned, " + item.participantCount + " participant(s)";
    }

    return (item.gameFinished ? "Finished " : "Playing ") + item.game;
}

function renderMeetings() {
    el.meetings.replaceChildren();

    for (const item of state.meetings) {
        const entry = create("li");
        const button = create("button");
        button.type = "button";
        button.classList.toggle("current", state.meeting !== null && state.meeting.id === item.id);
        button.append(create("span", "name", item.title),
            create("span", "when", formatDate(item.scheduledAt)),
            create("span", "state", meetingState(item)));
        button.addEventListener("click", () => {
            resetPlayState();
            run(() => api("POST", "/api/meetings/select", { id: item.id }),
                () => "Opened \"" + item.title + "\".");
        });

        entry.append(button);
        el.meetings.append(entry);
    }

    el.meetingsEmpty.textContent = state.meetings.length === 0
        ? "No saved meetings yet. Create the first one below." : "";
}

function renderMeeting() {
    const meeting = state.meeting;
    el.meetingInfo.replaceChildren();

    if (!meeting) {
        el.meetingInfo.textContent = "No meeting yet.";
        return;
    }

    const status = create("span", "status " + meeting.status, meeting.status.replace("_", " ").toLowerCase());
    el.meetingInfo.append(
        document.createTextNode("Meeting " + meeting.id + ": " + meeting.title + ", "
            + formatDate(meeting.scheduledAt) + ", " + meeting.availableMinutes + " minutes. "),
        status
    );
}

function renderParticipants() {
    el.participants.replaceChildren();

    for (const person of state.participants) {
        const item = create("li");
        const remove = create("button", null, "Remove");
        remove.type = "button";
        remove.disabled = !state.canEdit;
        remove.setAttribute("aria-label", "Remove " + person.name);
        remove.addEventListener("click", () =>
            run(() => api("DELETE", "/api/participants/" + person.id), () => person.name + " removed and saved."));

        item.append(create("span", "avatar", person.name.charAt(0).toUpperCase()),
            create("span", "name", person.name), remove);
        el.participants.append(item);
    }

    if (!state.meeting) {
        el.participantsEmpty.textContent = "Create a meeting to add participants.";
    } else if (state.participants.length === 0) {
        el.participantsEmpty.textContent = "No participants yet. Add the first one below.";
    } else {
        el.participantsEmpty.textContent = "";
    }

    el.name.disabled = !state.canEdit;
    el.addButton.disabled = !state.canEdit;
}

function renderStrategies() {
    el.strategies.replaceChildren();
    el.strategies.classList.toggle("disabled", !state.meeting);

    for (const strategy of state.strategies) {
        const label = create("label");
        const input = create("input");
        input.type = "radio";
        input.name = "strategy";
        input.checked = strategy.id === state.selectedStrategy;
        input.disabled = !state.meeting;
        input.addEventListener("change", () =>
            run(() => api("POST", "/api/strategy", { id: strategy.id }), null));

        label.append(input, document.createTextNode(strategy.name));
        el.strategies.append(label);
    }
}

function renderRecommendations() {
    const meeting = state.meeting;
    const games = state.recommendations;

    if (!games.some((game) => game.id === selectedGameId)) {
        selectedGameId = null;
    }

    el.recommendations.replaceChildren();

    for (const game of games) {
        const item = create("li");
        const label = create("label", "pick");
        const input = create("input");
        input.type = "radio";
        input.name = "game";
        input.value = game.id;
        input.checked = game.id === selectedGameId;
        input.disabled = !state.canEdit;
        input.addEventListener("change", () => {
            selectedGameId = game.id;
            updateStartButton();
        });

        label.append(input, gameCard(game, meeting.participantCount));
        item.append(label);
        el.recommendations.append(item);
    }

    if (!meeting) {
        el.recommendNote.textContent = "Create a meeting and add participants to see which games fit.";
    } else if (!state.canEdit) {
        el.recommendNote.textContent = "This meeting has started, so the group and the game can no longer change.";
    } else if (meeting.participantCount === 0) {
        el.recommendNote.textContent = "Add participants to see which games fit.";
    } else if (games.length === 0) {
        el.recommendNote.textContent = "No game fits " + meeting.participantCount + " player(s) in "
            + meeting.availableMinutes + " minutes. Change the group size or create a longer meeting.";
    } else {
        el.recommendNote.textContent = games.length + " game(s) fit " + meeting.participantCount
            + " player(s) in " + meeting.availableMinutes + " minutes. The yellow outline marks your group size.";
    }

    updateStartButton();
}

function updateStartButton() {
    el.startButton.disabled = !(state.canEdit && selectedGameId !== null);
}

function currentSession() {
    const sessions = state.meeting ? state.meeting.sessions : [];
    return sessions.length > 0 ? sessions[sessions.length - 1] : null;
}

function savedScore(session, playerId) {
    const result = session.results.find((row) => row.participantId === playerId);
    return result === undefined ? null : result.score;
}

function scoreInput(session, player) {
    const input = create("input");
    input.type = "text";
    input.inputMode = "numeric";
    input.setAttribute("aria-label", "Score for " + player.name);
    input.dataset.id = player.id;

    const saved = savedScore(session, player.id);
    input.value = drafts[player.id] !== undefined ? drafts[player.id] : (saved === null ? "" : String(saved));
    input.addEventListener("input", () => { drafts[player.id] = input.value; });
    return input;
}

function saveScoreButton(path, player, input, messageFor) {
    const button = create("button", null, "Save score");
    button.type = "button";
    button.setAttribute("aria-label", "Save score for " + player.name);
    button.addEventListener("click", () =>
        run(async () => {
            const result = await api("POST", path, { participantId: player.id, score: input.value });
            delete drafts[player.id];
            return result;
        }, messageFor));
    return button;
}

function renderPlay() {
    const session = currentSession();
    el.play.hidden = session === null;

    if (session === null) {
        return;
    }

    el.play.replaceChildren();

    if (!session.completed) {
        renderOpenGame(session);
    } else {
        renderFinishedGame(session);
    }
}

function renderOpenGame(session) {
    const form = create("form");
    form.autocomplete = "off";
    form.append(create("h3", null, session.game + " is being played"));
    form.append(create("p", "note",
        "Save each player's score as you go. Scores stay saved if you close the application. "
        + "Finish the game when every player has a score."));

    for (const player of session.players) {
        const row = create("label", "score-row");
        const input = scoreInput(session, player);
        row.append(create("span", null, player.name), input,
            saveScoreButton("/api/scores", player, input,
                () => "Score for " + player.name + " saved."));
        form.append(row);
    }

    const minutesRow = create("label", "score-row");
    const minutes = create("input");
    minutes.type = "text";
    minutes.inputMode = "numeric";
    minutes.value = minutesDraft !== null ? minutesDraft : session.gameMinutes;
    minutes.addEventListener("input", () => { minutesDraft = minutes.value; });
    minutesRow.append(create("span", null, "Minutes played"), minutes);
    form.append(minutesRow);

    const actions = create("div", "play-actions");
    const finish = create("button", "primary", "Finish game");
    finish.type = "submit";
    actions.append(finish);
    form.append(actions);

    form.addEventListener("submit", (event) => {
        event.preventDefault();
        const values = { minutes: minutes.value };

        for (const input of form.querySelectorAll("input[data-id]")) {
            values["score_" + input.dataset.id] = input.value;
        }

        run(() => api("POST", "/api/finish", values), () => {
            const done = currentSession();
            drafts = {};
            minutesDraft = null;
            return done.game + " finished. " + winnerText(done) + " Saved.";
        });
    });

    el.play.append(form);
}

function winnerText(session) {
    if (session.winners.length === 1) {
        return "Winner: " + session.winners[0] + ".";
    }

    return "It is a tie between " + session.winners.join(" and ") + ".";
}

function renderFinishedGame(session) {
    el.play.append(create("h3", null, session.game + " results"));
    el.play.append(create("p", "note",
        "Played " + session.actualMinutes + " minutes. " + winnerText(session)));

    const table = create("table", "results");
    const sorted = [...session.results].sort((a, b) => b.score - a.score);

    for (const row of sorted) {
        const player = { id: row.participantId, name: row.name };
        const tr = create("tr");
        const name = create("td", null, row.name);

        if (session.winners.includes(row.name)) {
            name.append(create("span", "badge", "winner"));
        }

        const score = create("td", "score");

        if (correcting) {
            const input = scoreInput(session, player);
            score.append(input, saveScoreButton("/api/scores/correct", player, input,
                () => "Score for " + player.name + " corrected. " + winnerText(currentSession()) + " Saved."));
        } else {
            score.textContent = String(row.score);
        }

        tr.append(name, score);
        table.append(tr);
    }

    el.play.append(table);

    const actions = create("div", "play-actions");
    const toggle = create("button", null, correcting ? "Done" : "Fix scores");
    toggle.type = "button";
    toggle.addEventListener("click", () => {
        correcting = !correcting;
        drafts = {};
        renderPlay();
    });
    actions.append(toggle);
    el.play.append(actions);

    const options = create("div", "report-options");

    for (const [key, text] of [["duration", "Include playing time"], ["scores", "Include scores"]]) {
        const label = create("label");
        const box = create("input");
        box.type = "checkbox";
        box.checked = reportOptions[key];
        box.addEventListener("change", () => {
            reportOptions[key] = box.checked;
            loadReport();
        });
        label.append(box, document.createTextNode(" " + text));
        options.append(label);
    }

    const report = create("pre", "report");
    report.id = "report";
    const download = create("button", null, "Download report");
    download.type = "button";
    download.addEventListener("click", downloadReport);

    el.play.append(create("h3", "report-title", "Meeting report"), options, report, download);
    loadReport();
}

async function loadReport() {
    const box = document.getElementById("report");

    if (!box) {
        return;
    }

    try {
        const query = "duration=" + (reportOptions.duration ? 1 : 0) + "&scores=" + (reportOptions.scores ? 1 : 0);
        const response = await fetch("/api/report?" + query);
        box.textContent = await response.text();
    } catch (error) {
        box.textContent = "Cannot load the report.";
    }
}

function downloadReport() {
    const box = document.getElementById("report");
    const link = document.createElement("a");
    link.href = URL.createObjectURL(new Blob([box.textContent], { type: "text/plain" }));
    link.download = "meeting-report.txt";
    link.click();
    URL.revokeObjectURL(link.href);
}

function render() {
    renderMeetings();
    renderMeeting();
    renderParticipants();
    renderStrategies();
    renderRecommendations();
    renderPlay();
}

/* ---------- user actions ---------- */

el.meetingForm.addEventListener("submit", (event) => {
    event.preventDefault();
    resetPlayState();

    run(() => api("POST", "/api/meeting",
            { title: el.title.value, date: el.when.value, minutes: el.minutes.value }),
        () => "Meeting created and saved. Add participants to get recommendations.");
});

el.participantForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const name = el.name.value.trim();

    run(async () => {
        const result = await api("POST", "/api/participants", { name: el.name.value });
        el.name.value = "";
        return result;
    }, () => name + " added and saved.");

    el.name.focus();
});

el.startButton.addEventListener("click", () => {
    if (selectedGameId === null) {
        showMessage("Select a game first.", "error");
        return;
    }

    const game = state.recommendations.find((item) => item.id === selectedGameId);

    run(() => api("POST", "/api/start", { gameId: selectedGameId }),
        () => game.title + " started and saved.");
});

/* ---------- start ---------- */

async function init() {
    setDefaultDate();

    try {
        catalog = await api("GET", "/api/games");
        state = await api("GET", "/api/state");
        renderCatalog();
        render();
    } catch (error) {
        el.catalogNote.textContent = "";
        showMessage(error.message, "error");
    }
}

init();
