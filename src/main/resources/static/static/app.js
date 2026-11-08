const page = document.querySelector("#page");
const dialog = document.querySelector("#create-dialog");
const userPicker = document.querySelector("#tracking-user");
const storageKey = "worktime.active-session";
const userKey = "worktime.selected-user";
let selectedUserId = readSelectedUser();
let active = readTimer();
let online = false;
let busy = false;
let routeVersion = 0;
let checkingConnection = false;

function readSelectedUser() {
  try {
    return Number(localStorage.getItem(userKey)) || 1;
  } catch {
    return 1;
  }
}

function timerKey(userId) {
  return userId === 1 ? storageKey : storageKey + "." + userId;
}

function readTimer(userId = selectedUserId) {
  try {
    const saved = JSON.parse(localStorage.getItem(timerKey(userId)));
    return saved?.id && saved.started_at ? saved : null;
  } catch {
    return null;
  }
}

function saveTimer(session, userId = selectedUserId) {
  if (userId === selectedUserId) active = session;
  try {
    if (session) localStorage.setItem(timerKey(userId), JSON.stringify(session));
    else localStorage.removeItem(timerKey(userId));
  } catch {
    notify("Browser storage is unavailable. The session can still be recovered from the server.", true);
  }
  renderTimer();
}

function selectUser(userId) {
  selectedUserId = userId;
  active = readTimer();
  try {
    localStorage.setItem(userKey, String(userId));
  } catch {
    // The selection remains available in this tab.
  }
  renderTimer();
}

function updateUserPicker(users) {
  if (!users.some(user => user.id === selectedUserId)) selectUser(users[0]?.id ?? 0);
  const options = users.map(user => {
    const option = element("option", user.name);
    option.value = user.id;
    return option;
  });
  if (!options.length) options.push(element("option", "No users"));
  userPicker.replaceChildren(...options);
  userPicker.value = selectedUserId ? String(selectedUserId) : "No users";
  renderTimer();
}

async function restoreTimer() {
  if (!selectedUserId || active || busy) return;
  const userId = selectedUserId;
  const session = await api("/sessions/active?userId=" + userId);
  if (session && selectedUserId === userId && !active && !busy) saveTimer(session);
}

function setOnline(value) {
  online = value;
  const indicator = document.querySelector("#connection");
  indicator.textContent = value ? "Online" : "Offline";
  indicator.classList.toggle("online", value);
  renderTimer();
}

async function api(path, method = "GET", data) {
  let response;
  try {
    response = await fetch("/api" + path, {
      method,
      cache: "no-store",
      signal: AbortSignal.timeout(8000),
      headers: data === undefined ? undefined : { "Content-Type": "application/json" },
      body: data === undefined ? undefined : JSON.stringify(data),
    });
  } catch {
    setOnline(false);
    throw new Error("The server is unavailable. Try again when you are online.");
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.detail || "Request failed (" + response.status + ").");
  }
  setOnline(true);
  return path === "/sessions/export" ? response.blob() : response.json();
}

async function checkConnection() {
  if (checkingConnection) return;
  checkingConnection = true;
  const wasOnline = online;
  try {
    await api("/health");
    const users = await api("/employees");
    updateUserPicker(users);
    let retriedStop = false;
    for (const user of users) {
      const session = user.id === selectedUserId ? active : readTimer(user.id);
      if (session?.pending_stop_at && !busy) {
        await stopTimer(session);
        retriedStop = true;
      }
    }
    await restoreTimer();
    if (!wasOnline) void renderRoute();
    else if (!retriedStop && location.hash === "#users") {
      const tasks = new Map(users.map(user => [String(user.id), user.active_task]));
      page.querySelectorAll("[data-user-activity]").forEach(cell => {
        cell.textContent = tasks.get(cell.dataset.userActivity) || "Idle";
      });
    }
  } catch {
    setOnline(false);
  } finally {
    checkingConnection = false;
  }
}

function element(tag, value, className) {
  const node = document.createElement(tag);
  if (value !== undefined) node.textContent = value;
  if (className) node.className = className;
  return node;
}

function link(label, hash) {
  const node = element("a", label);
  node.href = hash;
  return node;
}

function button(label, action, style = "outline") {
  const node = element("button", label, "button " + style);
  node.type = "button";
  node.addEventListener("click", action);
  return node;
}

function notify(message = "", error = false) {
  const notice = document.querySelector("#notice");
  notice.hidden = !message;
  notice.textContent = message;
  notice.classList.toggle("error", error);
}

function duration(seconds) {
  const whole = Math.max(0, Math.floor(seconds));
  return [Math.floor(whole / 3600), Math.floor(whole / 60) % 60, whole % 60]
    .map(part => String(part).padStart(2, "0")).join(":");
}

function renderTimer() {
  userPicker.disabled = busy || !selectedUserId || !userPicker.options.length;
  document.querySelectorAll("[data-executed]").forEach(clock => {
    const elapsed = (Date.now() - Number(clock.dataset.snapshot)) / 1000;
    clock.textContent = duration(Number(clock.dataset.executed) + elapsed * Number(clock.dataset.runningCount));
  });
  document.querySelector("#running-timer").hidden = !active;
  document.querySelector("#timer-idle").hidden = Boolean(active);
  document.querySelector("#stop-timer").disabled = busy;
  document.querySelectorAll("[data-task]").forEach(control => {
    const running = active?.task_id === Number(control.dataset.task);
    control.textContent = running ? "■ Stop" : "▶ Start";
    control.disabled = busy || (!running && (Boolean(active) || !online || !selectedUserId));
  });
  if (active) {
    document.querySelector("#timer-label").textContent =
      (active.project_name || "Unassigned") + " : " + active.description;
    const end = active.pending_stop_at ? Date.parse(active.pending_stop_at) : Date.now();
    document.querySelector("#timer-clock").textContent = duration((end - Date.parse(active.started_at)) / 1000);
    document.querySelector("#stop-timer").textContent = active.pending_stop_at ? "■ Retry stop" : "■ Stop";
  }
}

async function startTask(id) {
  if (busy || active || !online || !selectedUserId) return;
  const userId = selectedUserId;
  busy = true;
  renderTimer();
  try {
    saveTimer(await api("/tasks/" + id + "/sessions?userId=" + userId, "POST"), userId);
    await renderRoute();
    notify();
  } catch (error) {
    notify(error.message, true);
  } finally {
    busy = false;
    renderTimer();
  }
}

async function stopTimer(timer = active) {
  if (!timer || busy) return;
  busy = true;
  const userId = timer.user_id;
  const session = { ...timer, pending_stop_at: timer.pending_stop_at || new Date().toISOString() };
  saveTimer(session, userId);
  try {
    const saved = await api("/sessions/" + session.id + "/stop?userId=" + userId, "POST",
      { stopped_at: session.pending_stop_at });
    if (saved.id !== session.id || !saved.stopped_at) throw new Error("Stop was not confirmed.");
    const current = userId === selectedUserId ? active : readTimer(userId);
    if (current?.id === saved.id) saveTimer(null, userId);
    await renderRoute();
    notify("Work session saved.");
  } catch (error) {
    notify(error.message + " The stop is pending. The app will retry automatically.", true);
  } finally {
    busy = false;
    renderTimer();
  }
}

function heading(title, action) {
  const header = element("div", undefined, "page-heading");
  header.append(element("h1", title));
  if (action) header.append(action);
  return header;
}

function table(headers, rows, empty) {
  const panel = element("div", undefined, "panel table-scroll");
  const grid = element("table");
  const header = grid.createTHead().insertRow();
  headers.forEach(value => {
    const cell = element("th");
    cell.append(value);
    cell.scope = "col";
    header.append(cell);
  });
  const body = grid.createTBody();
  for (const values of rows) {
    const row = body.insertRow();
    for (const value of values) {
      const cell = row.insertCell();
      if (value instanceof Node) cell.append(value);
      else cell.textContent = value;
    }
  }
  if (!rows.length) {
    const cell = body.insertRow().insertCell();
    cell.colSpan = headers.length;
    cell.className = "empty-report";
    cell.textContent = empty;
  }
  panel.append(grid);
  return panel;
}

function paginatedTable(headers, rows, empty, label) {
  const section = element("div");
  const panel = table(headers, rows, empty);
  if (!rows.length) {
    section.append(panel);
    return section;
  }

  const rowElements = panel.querySelectorAll("tbody tr");
  const pagination = element("nav", undefined, "report-pagination");
  pagination.setAttribute("aria-label", label + " pagination");
  const sizeLabel = element("label", "Rows per page ");
  const pageSize = element("select");
  for (const size of [10, 20, 50, 100]) {
    const option = element("option", String(size));
    option.value = size;
    pageSize.append(option);
  }
  sizeLabel.append(pageSize);
  let currentPage = 1;
  const previous = button("Previous", () => {
    currentPage--;
    updatePage();
  });
  const next = button("Next", () => {
    currentPage++;
    updatePage();
  });
  const status = element("span");
  status.setAttribute("role", "status");
  pageSize.addEventListener("change", () => {
    currentPage = 1;
    updatePage();
  });
  pagination.append(sizeLabel, previous, status, next);
  section.append(panel, pagination);
  updatePage();
  return section;

  function updatePage() {
    const size = Number(pageSize.value);
    const pageCount = Math.ceil(rows.length / size);
    const offset = (currentPage - 1) * size;
    rowElements.forEach((row, index) => {
      row.hidden = index < offset || index >= offset + size;
    });
    previous.disabled = currentPage === 1;
    next.disabled = currentPage === pageCount;
    status.textContent = "Page " + currentPage + " of " + pageCount + " · " + rows.length + " rows";
  }
}

function openCreate(title, label, maxLength, submit, withEstimate = false) {
  const input = document.querySelector("#dialog-input");
  const error = document.querySelector("#dialog-error");
  const form = document.querySelector("#create-form");
  document.querySelector("#dialog-title").textContent = title;
  document.querySelector("#dialog-label").textContent = label;
  input.value = "";
  input.maxLength = maxLength;
  const estimate = document.querySelector("#dialog-estimate");
  document.querySelector("#estimate-field").hidden = !withEstimate;
  estimate.disabled = !withEstimate;
  estimate.required = withEstimate;
  estimate.value = "0";
  error.hidden = true;
  form.onsubmit = async event => {
    event.preventDefault();
    const save = form.querySelector("[type=submit]");
    if (save.disabled) return;
    save.disabled = true;
    error.hidden = true;
    try {
      await submit(input.value.trim(), estimate.valueAsNumber);
      dialog.close();
      await renderRoute();
    } catch (failure) {
      error.textContent = failure.message;
      error.hidden = false;
    } finally {
      save.disabled = false;
    }
  };
  dialog.showModal();
}

async function usersPage() {
  const users = await api("/employees");
  updateUserPicker(users);
  const create = button("Add user", () => openCreate("Add user", "Name", 80,
    name => api("/employees", "POST", { name })), "primary");
  return [heading("Users", create),
    paginatedTable(["ID", "Name", "Current task", "Action"], users.map(user => {
      const activity = element("span", user.active_task || "Idle");
      activity.dataset.userActivity = user.id;
      return [user.id, user.name, activity, link("Edit and view statistics", "#users/" + user.id)];
    }), "No users yet. Add a user to start tracking tasks.", "Users")];
}

async function userPage(id) {
  const [users, sessions] = await Promise.all([api("/employees"), api("/sessions?userId=" + id)]);
  const user = users.find(candidate => candidate.id === id);
  if (!user) throw new Error("User not found.");
  const form = element("form", undefined, "panel workspace-form");
  const label = element("label", "Name");
  const input = element("input");
  input.value = user.name;
  input.required = true;
  input.maxLength = 80;
  label.append(input);
  const save = element("button", "Save changes", "button primary");
  form.append(label, save);
  form.onsubmit = async event => {
    event.preventDefault();
    save.disabled = true;
    try {
      await api("/employees/" + id, "PUT", { name: input.value.trim() });
      await renderRoute();
      notify("User updated.");
    } catch (error) {
      notify(error.message, true);
    } finally {
      save.disabled = false;
    }
  };
  const groups = new Map();
  for (const session of sessions) {
    const month = localDate(session.started_at).slice(0, 7);
    const key = month + "/" + (session.project_id ?? "");
    if (!groups.has(key)) groups.set(key, {
      month, project: session.project_name || "Unassigned", count: 0, seconds: 0,
    });
    const group = groups.get(key);
    group.count++;
    group.seconds += session.duration_seconds;
  }
  const rows = [...groups.values()].sort((a, b) =>
    b.month.localeCompare(a.month) || a.project.localeCompare(b.project));
  return [link("← All users", "#users"), heading("Edit " + user.name), form,
    element("h2", "Sessions by project and start month"),
    paginatedTable(["Month", "Project", "Sessions", "Duration"], rows.map(group => [
      group.month, group.project, group.count, duration(group.seconds),
    ]), "No sessions for this user.", "User statistics")];
}

async function projectsPage() {
  const projects = await api("/projects");
  const create = button("Add project", () => openCreate("Add project", "Project name", 80,
    name => api("/projects", "POST", { name })), "primary");
  return [heading("Projects", create), paginatedTable(["ID", "Project", "Tasks", "Estimated", "Executed", "Status"], projects.map(project => [
    project.id, link(project.name, "#projects/" + project.id), project.task_count, ...timingCells(project),
  ]), "No projects yet.", "Projects")];
}

function timingCells(item) {
  const executed = element("span", duration(item.executed_seconds));
  if (item.working) {
    executed.dataset.executed = item.executed_seconds;
    executed.dataset.snapshot = Date.now();
    // Older responses report a running timer without a session count.
    executed.dataset.runningCount = item.running_count ?? 1;
  }
  return [duration(item.estimated_minutes * 60), executed,
    element("span", item.working ? "Working" : "Idle", item.working ? "pill" : "pill violet")];
}

async function projectPage(id) {
  const project = await api("/projects/" + id);
  const create = button("Add task", () => openCreate("Add task", "Task description", 300,
    (description, estimated_minutes) =>
      api("/projects/" + id + "/tasks", "POST", { description, estimated_minutes }), true), "primary");
  return [link("← All projects", "#projects"), heading(project.name, create),
    table(["Estimated", "Executed", "Status"], [timingCells(project)], ""),
    element("h2", "Tasks"),
    paginatedTable(["Task ID", "Description", "Estimated", "Executed", "Status", "Timer"], project.tasks.map(task => {
      const control = button("▶ Start", () => active?.task_id === task.id ? stopTimer() : startTask(task.id));
      control.dataset.task = task.id;
      return [task.id, task.description, ...timingCells(task), control];
    }), "No tasks yet. Add a task to start tracking.", "Tasks")];
}

function localDate(instant) {
  const date = new Date(instant);
  return [date.getFullYear(), date.getMonth() + 1, date.getDate()]
    .map(value => String(value).padStart(2, "0")).join("-");
}

async function reportsPage() {
  const [sessions, users, projects] = await Promise.all([
    api("/sessions"), api("/employees"), api("/projects"),
  ]);
  const filters = element("div", undefined, "panel workspace-form filters");
  const results = element("div");
  function field(label, control) {
    const wrapper = element("label", label);
    wrapper.append(control);
    filters.append(wrapper);
    control.addEventListener("input", update);
    return control;
  }
  function select(label, options) {
    const control = element("select");
    for (const [value, text] of options) {
      const option = element("option", text);
      option.value = value;
      control.append(option);
    }
    return field(label, control);
  }
  const user = select("User", [["", "All users"], ...users.map(row => [row.id, row.name])]);
  const project = select("Project", [["", "All projects"], ["unassigned", "Unassigned"],
    ...projects.map(row => [row.id, row.name])]);
  const status = select("Status", [["", "All statuses"], ["running", "Running"], ["completed", "Completed"]]);
  const from = field("Start date from", element("input"));
  const to = field("Start date through", element("input"));
  from.type = to.type = "date";
  const sort = select("Sort by", [["newest", "Newest start"], ["oldest", "Oldest start"],
    ["longest", "Longest duration"], ["shortest", "Shortest duration"],
    ["user", "User name"], ["project", "Project name"]]);
  const pageSize = select("Rows per page", [20, 50, 100, 200].map(size => [size, String(size)]));
  const totalTime = element("p");
  totalTime.setAttribute("role", "status");
  const userStatistics = element("section");
  const statisticsTable = element("div");
  userStatistics.append(element("h2", "Time by user"), statisticsTable);
  let currentPage = 1;
  const pagination = element("nav", undefined, "report-pagination");
  pagination.setAttribute("aria-label", "Report pagination");
  const previous = button("Previous", () => {
    currentPage--;
    renderPage();
  });
  const next = button("Next", () => {
    currentPage++;
    renderPage();
  });
  const pageStatus = element("span");
  pageStatus.setAttribute("role", "status");
  pagination.append(previous, pageStatus, next);
  let visible = [];
  const selected = new Set();
  const selectAll = element("input");
  selectAll.type = "checkbox";
  selectAll.setAttribute("aria-label", "Select all filtered sessions");
  selectAll.title = "Select all filtered sessions across pages";
  selectAll.addEventListener("change", () => {
    for (const row of visible) {
      if (selectAll.checked) selected.add(row.id);
      else selected.delete(row.id);
    }
    results.querySelectorAll("tbody input[type=checkbox]").forEach(checkbox => {
      checkbox.checked = selectAll.checked;
    });
    updateSelection();
  });
  let exporting = false;
  const exportButton = button("Export selected to Excel", async () => {
    const ids = visible.filter(row => selected.has(row.id)).map(row => row.id);
    if (exporting || !ids.length) return;
    exporting = true;
    updateSelection();
    try {
      const file = await api("/sessions/export", "POST", ids);
      const url = URL.createObjectURL(file);
      const download = element("a");
      download.href = url;
      download.download = "work-sessions.xlsx";
      download.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      notify(error.message, true);
    } finally {
      exporting = false;
      updateSelection();
    }
  }, "primary");
  function updateSelection() {
    const count = visible.filter(row => selected.has(row.id)).length;
    exportButton.disabled = exporting || count === 0;
    selectAll.disabled = visible.length === 0;
    selectAll.checked = count > 0 && count === visible.length;
    selectAll.indeterminate = count > 0 && count < visible.length;
  }

  function sessionCheckbox(row) {
    const checkbox = element("input");
    checkbox.type = "checkbox";
    checkbox.checked = selected.has(row.id);
    checkbox.setAttribute("aria-label", "Select session " + row.id);
    checkbox.addEventListener("change", () => {
      if (checkbox.checked) selected.add(row.id);
      else selected.delete(row.id);
      updateSelection();
    });
    return checkbox;
  }
  const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "medium" });
  function update() {
    const comparisons = {
      newest: (a, b) => Date.parse(b.started_at) - Date.parse(a.started_at),
      oldest: (a, b) => Date.parse(a.started_at) - Date.parse(b.started_at),
      longest: (a, b) => b.duration_seconds - a.duration_seconds,
      shortest: (a, b) => a.duration_seconds - b.duration_seconds,
      user: (a, b) => a.user_name.localeCompare(b.user_name),
      project: (a, b) => (a.project_name || "Unassigned").localeCompare(b.project_name || "Unassigned"),
    };
    visible = sessions.filter(session => {
      const day = localDate(session.started_at);
      return (!user.value || Number(user.value) === session.user_id)
        && (!project.value || project.value === String(session.project_id ?? "unassigned"))
        && (!status.value || status.value === (session.stopped_at ? "completed" : "running"))
        && (!from.value || day >= from.value) && (!to.value || day <= to.value);
    }).sort(comparisons[sort.value]);
    const totalsByUser = new Map();
    let totalSeconds = 0;
    for (const row of visible) {
      totalSeconds += row.duration_seconds;
      totalsByUser.set(row.user_id, (totalsByUser.get(row.user_id) ?? 0) + row.duration_seconds);
    }
    totalTime.textContent = "Total time for filtered sessions: " + duration(totalSeconds);
    const userRows = users.filter(person => !user.value || String(person.id) === user.value)
      .map(person => [person.name, duration(totalsByUser.get(person.id) ?? 0)]);
    statisticsTable.replaceChildren(paginatedTable(["User", "Total time"], userRows, "No users.", "Time by user"));
    currentPage = 1;
    renderPage();
  }

  function renderPage() {
    const size = Number(pageSize.value);
    const pageCount = Math.max(1, Math.ceil(visible.length / size));
    const offset = (currentPage - 1) * size;
    const rows = visible.slice(offset, offset + size);
    results.replaceChildren(table([selectAll, "ID", "User", "Project", "Task", "Start", "Stop", "Status", "Duration"],
      rows.map(row => [
        sessionCheckbox(row), row.id, row.user_name, row.project_name || "Unassigned", row.description,
        dateTime.format(new Date(row.started_at)), row.stopped_at ? dateTime.format(new Date(row.stopped_at)) : "—",
        row.stopped_at ? "Completed" : "Running", duration(row.duration_seconds),
      ]), "No sessions match these filters."));
    previous.disabled = currentPage === 1;
    next.disabled = currentPage === pageCount;
    pageStatus.textContent = visible.length
      ? "Page " + currentPage + " of " + pageCount + " · " + visible.length + " sessions"
      : "0 sessions";
    updateSelection();
  }
  update();
  return [heading("Reports", exportButton), filters, totalTime, userStatistics,
    element("h2", "Work sessions"), results, pagination];
}

async function renderRoute() {
  const version = ++routeVersion;
  const [section, rawId] = (location.hash.slice(1) || "projects").split("/");
  const id = Number(rawId);
  document.querySelectorAll(".sidebar nav a").forEach(item => {
    const selected = item.hash === "#" + section;
    item.classList.toggle("selected", selected);
    if (selected) item.setAttribute("aria-current", "page");
    else item.removeAttribute("aria-current");
  });
  try {
    let content;
    if (section === "users") content = await (id ? userPage(id) : usersPage());
    else if (section === "reports") content = await reportsPage();
    else content = await (id ? projectPage(id) : projectsPage());
    if (version !== routeVersion) return;
    page.replaceChildren(...content);
    renderTimer();
  } catch (error) {
    if (version !== routeVersion) return;
    page.replaceChildren(element("p", error.message, "notice error"),
      button("Retry", renderRoute));
  }
}

document.querySelector("#stop-timer").addEventListener("click", () => stopTimer());
userPicker.addEventListener("change", async () => {
  selectUser(Number(userPicker.value));
  notify();
  try {
    if (online) await restoreTimer();
  } catch (error) {
    notify(error.message, true);
  }
});
document.querySelector("#cancel-dialog").addEventListener("click", () => dialog.close());
window.addEventListener("hashchange", () => {
  notify();
  void renderRoute();
});
window.addEventListener("storage", event => {
  if (event.key === timerKey(selectedUserId)) {
    active = readTimer();
    renderTimer();
    if (!location.hash || location.hash.startsWith("#projects")) void renderRoute();
  }
});
window.addEventListener("online", checkConnection);
window.addEventListener("offline", () => setOnline(false));
setInterval(renderTimer, 500);
setInterval(checkConnection, 15000);
renderTimer();
void renderRoute();
void checkConnection();

if ("serviceWorker" in navigator) navigator.serviceWorker.register("/service-worker.js").catch(() => {});
const install = document.querySelector("#install-app");
window.addEventListener("beforeinstallprompt", event => {
  event.preventDefault();
  install.hidden = false;
  install.onclick = async () => {
    await event.prompt();
    install.hidden = true;
  };
});
window.addEventListener("appinstalled", () => { install.hidden = true; });
