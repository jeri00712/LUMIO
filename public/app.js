// ==========================================
// CONSTANTS & INITIAL DATA
// ==========================================
const GRADES = ["S", "A+", "A", "B+", "B", "C", "U"];

const GRADE_COLORS = {
    "S": "#10b981",
    "A+": "#06b6d4",
    "A": "#3b82f6",
    "B+": "#8b5cf6",
    "B": "#f59e0b",
    "C": "#f97316",
    "U": "#ef4444"
};

// No demo/default academic data is loaded.

// State
let lastCalculatedGpaResult = null;
let savedHistoryRecords = [];
let selectedUploadFile = null;
let uploadedFileRecord = null;

// ==========================================
// DOM ELEMENTS
// ==========================================

// Tabs
const tabGpaBtn = document.getElementById("tabGpaBtn");
const tabCgpaBtn = document.getElementById("tabCgpaBtn");
const tabHistoryBtn = document.getElementById("tabHistoryBtn");
const tabUploadBtn = document.getElementById("tabUploadBtn");

const gpaTabSection = document.getElementById("gpaTabSection");
const cgpaTabSection = document.getElementById("cgpaTabSection");
const historyTabSection = document.getElementById("historyTabSection");
const uploadTabSection = document.getElementById("uploadTabSection");

// GPA Form
const subjectsBody = document.getElementById("subjectsBody");
const addSubjectBtn = document.getElementById("addSubjectBtn");
const resetBtn = document.getElementById("resetBtn");
const gpaForm = document.getElementById("gpaForm");
const resultsSection = document.getElementById("resultsSection");
const saveGpaToHistoryBtn = document.getElementById("saveGpaToHistoryBtn");
const semesterSaveTitle = document.getElementById("semesterSaveTitle");

// CGPA Form
const semestersBody = document.getElementById("semestersBody");
const addSemesterBtn = document.getElementById("addSemesterBtn");
const resetCgpaBtn = document.getElementById("resetCgpaBtn");
const cgpaForm = document.getElementById("cgpaForm");
const cgpaResultsSection = document.getElementById("cgpaResultsSection");

// History
const historyCardsContainer = document.getElementById("historyCardsContainer");
const historyEmptyState = document.getElementById("historyEmptyState");
const historySummaryText = document.getElementById("historySummaryText");
const syncToCgpaBtn = document.getElementById("syncToCgpaBtn");
const clearAllHistoryBtn = document.getElementById("clearAllHistoryBtn");
const goToGpaFromEmptyBtn = document.getElementById("goToGpaFromEmptyBtn");

// Upload
const marksheetDropzone = document.getElementById("marksheetDropzone");
const marksheetFileInput = document.getElementById("marksheetFileInput");
const browseFilesBtn = document.getElementById("browseFilesBtn");
const uploadPreviewCard = document.getElementById("uploadPreviewCard");
const previewIcon = document.getElementById("previewIcon");
const previewFileName = document.getElementById("previewFileName");
const previewFileMeta = document.getElementById("previewFileMeta");
const cancelFileBtn = document.getElementById("cancelFileBtn");
const uploadFileBtn = document.getElementById("uploadFileBtn");
const uploadProgressContainer = document.getElementById("uploadProgressContainer");
const uploadProgressBar = document.getElementById("uploadProgressBar");
const uploadProgressText = document.getElementById("uploadProgressText");
const uploadSuccessCard = document.getElementById("uploadSuccessCard");
const uploadTimestamp = document.getElementById("uploadTimestamp");
const uploadSuccessDetails = document.getElementById("uploadSuccessDetails");
const extractionCard = document.getElementById("extractionCard");
const extractedSubjectsBody = document.getElementById("extractedSubjectsBody");
const extractedTextBox = document.getElementById("extractionTextBox");
const extractedText = document.getElementById("extractedText");
const clearExtractionBtn = document.getElementById("clearExtractionBtn");
const useExtractedDataBtn = document.getElementById("useExtractedDataBtn");

// ==========================================
// INITIALIZATION
// ==========================================
document.addEventListener("DOMContentLoaded", () => {
    initTabs();
    initGpaCalculator();
    initCgpaCalculator();
    initHistory();
    initUpload();
    switchTab("gpa");
});

// ==========================================
// TAB SWITCHING
// ==========================================
function initTabs() {
    if (tabGpaBtn) {
        tabGpaBtn.addEventListener("click", () => switchTab("gpa"));
    }

    if (tabCgpaBtn) {
        tabCgpaBtn.addEventListener("click", () => switchTab("cgpa"));
    }

    if (tabHistoryBtn) {
        tabHistoryBtn.addEventListener("click", () => switchTab("history"));
    }

    if (tabUploadBtn) {
        tabUploadBtn.addEventListener("click", () => switchTab("upload"));
    }

    if (goToGpaFromEmptyBtn) {
        goToGpaFromEmptyBtn.addEventListener("click", () => switchTab("gpa"));
    }
}

function switchTab(tab) {
    if (tabGpaBtn) tabGpaBtn.classList.toggle("active", tab === "gpa");
    if (tabCgpaBtn) tabCgpaBtn.classList.toggle("active", tab === "cgpa");
    if (tabHistoryBtn) tabHistoryBtn.classList.toggle("active", tab === "history");
    if (tabUploadBtn) tabUploadBtn.classList.toggle("active", tab === "upload");

    if (gpaTabSection) {
        gpaTabSection.style.display = tab === "gpa" ? "block" : "none";
    }

    if (cgpaTabSection) {
        cgpaTabSection.style.display = tab === "cgpa" ? "block" : "none";
    }

    if (historyTabSection) {
        historyTabSection.style.display = tab === "history" ? "block" : "none";
    }

    if (uploadTabSection) {
        uploadTabSection.style.display = tab === "upload" ? "block" : "none";
    }

    if (tab === "history") {
        loadHistory();
    }
}

// ==========================================
// GPA CALCULATOR
// ==========================================
function initGpaCalculator() {
    if (addSubjectBtn) {
        addCourseRow();

        addSubjectBtn.addEventListener("click", () => {
            addCourseRow();
        });
    }

    if (resetBtn) {
        resetBtn.addEventListener("click", () => {
            subjectsBody.innerHTML = "";
            addCourseRow();
            resultsSection.style.display = "none";
            lastCalculatedGpaResult = null;
        });
    }

    if (gpaForm) {
        gpaForm.addEventListener("submit", handleGpaFormSubmit);
    }

    if (saveGpaToHistoryBtn) {
        saveGpaToHistoryBtn.addEventListener("click", handleSaveGpaToHistory);
    }
}

function addCourseRow(name = "", credit = "", selectedGrade = "") {
    const tr = document.createElement("tr");

    const gradeOptions =
        `<option value="">Select</option>` +
        GRADES.map(grade =>
            `<option value="${grade}" ${grade === selectedGrade ? "selected" : ""}>${grade}</option>`
        ).join("");

    tr.innerHTML = `
        <td><span class="row-index"></span></td>

        <td>
            <input
                type="text"
                class="sub-name"
                placeholder="e.g. Operating Systems"
                value="${escapeHtml(name)}"
                required
            />
        </td>

        <td>
            <input
                type="number"
                class="sub-credit"
                min="0"
                max="12"
                step="1"
                value="${credit === "" ? "" : Number(credit)}"
                required
            />
        </td>

        <td>
            <select class="sub-grade" required>
                ${gradeOptions}
            </select>
        </td>

        <td style="text-align:center;">
            <button type="button" class="btn-delete" title="Remove course">✕</button>
        </td>
    `;

    tr.querySelector(".btn-delete").addEventListener("click", () => {
        if (subjectsBody.children.length > 1) {
            tr.remove();
            updateRowIndices();
        } else {
            alert("At least one subject is required.");
        }
    });

    subjectsBody.appendChild(tr);
    updateRowIndices();
}

function updateRowIndices() {
    if (!subjectsBody) return;

    subjectsBody.querySelectorAll("tr").forEach((row, index) => {
        const span = row.querySelector(".row-index");

        if (span) {
            span.textContent = index + 1;
        }
    });
}

async function handleGpaFormSubmit(event) {
    event.preventDefault();

    const rows = subjectsBody.querySelectorAll("tr");
    const subjects = [];

    for (const row of rows) {
        const name = row.querySelector(".sub-name").value.trim();
        const creditInput = row.querySelector(".sub-credit").value;
        const grade = row.querySelector(".sub-grade").value;
        const credit = Number(creditInput);

        if (!name) {
            alert("Please enter a subject name.");
            return;
        }

        if (creditInput === "" || !Number.isInteger(credit) || credit < 0 || credit > 12) {
            alert("Credits must be a whole number between 0 and 12.");
            return;
        }

        if (!grade) {
            alert(`Please select a grade for "${name}".`);
            return;
        }

        subjects.push({ name, credit, grade });
    }

    if (subjects.length === 0) {
        alert("Please add at least one subject.");
        return;
    }

    if (!subjects.some(subject => subject.credit > 0)) {
        alert("At least one subject must have credits greater than zero.");
        return;
    }

    const calculateBtn = document.getElementById("calculateBtn");

    if (!calculateBtn) return;

    const originalText = calculateBtn.textContent;
    calculateBtn.textContent = "Calculating...";
    calculateBtn.disabled = true;

    try {
        const response = await fetch("/api/calculate", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(subjects)
        });

        const responseText = await response.text();

        let data;

        try {
            data = JSON.parse(responseText);
        } catch {
            throw new Error("The GPA server returned invalid JSON.");
        }

        if (!response.ok) {
            throw new Error(data.message || data.error || `HTTP ${response.status}`);
        }

        lastCalculatedGpaResult = data;
        displayGpaResults(data);

    } catch (error) {
        console.error("GPA calculation error:", error);
        alert("GPA calculation failed: " + error.message);
    } finally {
        calculateBtn.textContent = originalText;
        calculateBtn.disabled = false;
    }
}

function displayGpaResults(data) {
    resultsSection.style.display = "block";

    document.getElementById("gpaScore").textContent = Number(data.gpa).toFixed(2);
    document.getElementById("totalCredits").textContent = data.totalCredits;
    document.getElementById("totalPoints").textContent = data.totalCreditPoints;

    const remarksElem = document.getElementById("gpaRemarks");

    if (data.gpa >= 9) {
        remarksElem.textContent = "🌟 Outstanding Performance (10-point scale)";
    } else if (data.gpa >= 8) {
        remarksElem.textContent = "✨ First Class with Distinction";
    } else if (data.gpa >= 6.5) {
        remarksElem.textContent = "👍 First Class";
    } else if (data.gpa >= 5) {
        remarksElem.textContent = "Second Class";
    } else {
        remarksElem.textContent = "Needs Improvement";
    }

    if (data.analysis) {
        const analysis = data.analysis;
        const gradeCounts = analysis.gradeCounts || {};

        document.getElementById("quickTopSubject").textContent =
            analysis.topSubject || "--";

        document.getElementById("quickPassRate").textContent =
            `${analysis.passRate ?? 100}%`;

        const distinctionCount =
            (gradeCounts["S"] || 0) +
            (gradeCounts["A+"] || 0) +
            (gradeCounts["A"] || 0);

        document.getElementById("quickDistinction").textContent =
            `${distinctionCount} of ${(data.subjects || []).length} courses`;
    }

    const breakdownBody = document.getElementById("breakdownBody");
    breakdownBody.innerHTML = "";

    (data.subjects || []).forEach(subject => {
        const tr = document.createElement("tr");

        tr.innerHTML = `
            <td><strong>${escapeHtml(subject.name)}</strong></td>
            <td>${subject.credit}</td>
            <td><span class="badge-grade">${escapeHtml(subject.grade)}</span></td>
            <td>${subject.gradePoint}</td>
            <td><strong>${subject.creditPoint}</strong></td>
        `;

        breakdownBody.appendChild(tr);
    });

    resultsSection.scrollIntoView({ behavior: "smooth", block: "start" });
}

// ==========================================
// CGPA CALCULATOR
// ==========================================
function initCgpaCalculator() {
    if (addSemesterBtn) {
        addSemesterRow();

        addSemesterBtn.addEventListener("click", () => {
            addSemesterRow();
        });
    }

    if (resetCgpaBtn) {
        resetCgpaBtn.addEventListener("click", () => {
            semestersBody.innerHTML = "";
            addSemesterRow();
            cgpaResultsSection.style.display = "none";
        });
    }

    if (cgpaForm) {
        cgpaForm.addEventListener("submit", handleCgpaFormSubmit);
    }
}

function addSemesterRow(name = "", gpa = "", credits = "") {
    const tr = document.createElement("tr");

    tr.innerHTML = `
        <td><span class="sem-index"></span></td>

        <td>
            <input type="text" class="sem-name" value="${escapeHtml(name)}" required />
        </td>

        <td>
            <input
                type="number"
                class="sem-gpa"
                min="0"
                max="10"
                step="0.01"
                value="${gpa === "" ? "" : Number(gpa).toFixed(2)}"
                placeholder="e.g. 8.50"
                required
            />
        </td>

        <td>
            <input
                type="number"
                class="sem-credits"
                min="1"
                max="50"
                value="${credits}"
                placeholder="e.g. 20"
                required
            />
        </td>

        <td style="text-align:center;">
            <button type="button" class="btn-delete" title="Remove semester">✕</button>
        </td>
    `;

    tr.querySelector(".btn-delete").addEventListener("click", () => {
        if (semestersBody.children.length > 1) {
            tr.remove();
            updateSemesterIndices();
        } else {
            alert("At least one semester is required.");
        }
    });

    semestersBody.appendChild(tr);
    updateSemesterIndices();
}

function updateSemesterIndices() {
    if (!semestersBody) return;

    semestersBody.querySelectorAll("tr").forEach((row, index) => {
        const span = row.querySelector(".sem-index");

        if (span) {
            span.textContent = index + 1;
        }
    });
}

async function handleCgpaFormSubmit(event) {
    if (event && event.preventDefault) {
        event.preventDefault();
    }

    const rows = semestersBody.querySelectorAll("tr");
    const semesters = [];

    for (const [index, row] of Array.from(rows).entries()) {
        const name = row.querySelector(".sem-name").value.trim();
        const gpaValue = row.querySelector(".sem-gpa").value;
        const creditValue = row.querySelector(".sem-credits").value;

        const gpa = Number(gpaValue);
        const credits = Number(creditValue);

        if (!name || gpaValue === "" || !Number.isFinite(gpa) || gpa < 0 || gpa > 10) {
            alert(`Please enter a valid name and GPA for Semester ${index + 1}.`);
            return;
        }

        if (creditValue === "" || !Number.isInteger(credits) || credits < 1 || credits > 50) {
            alert(`Please enter valid credits for Semester ${index + 1}.`);
            return;
        }

        semesters.push({
            semester: index + 1,
            gpa,
            credits
        });
    }

    if (semesters.length === 0) {
        alert("Please add at least one semester.");
        return;
    }

    const calculateCgpaBtn = document.getElementById("calculateCgpaBtn");

    if (!calculateCgpaBtn) return;

    const originalText = calculateCgpaBtn.textContent;
    calculateCgpaBtn.textContent = "Calculating...";
    calculateCgpaBtn.disabled = true;

    try {
        const response = await fetch("/api/calculate-cgpa", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(semesters)
        });

        const responseText = await response.text();
        let data;

        try {
            data = JSON.parse(responseText);
        } catch {
            throw new Error("The CGPA server returned invalid JSON.");
        }

        if (!response.ok) {
            throw new Error(data.message || data.error || `HTTP ${response.status}`);
        }

        displayCgpaResults(data);

    } catch (error) {
        console.error("CGPA calculation error:", error);
        alert("CGPA calculation failed: " + error.message);
    } finally {
        calculateCgpaBtn.textContent = originalText;
        calculateCgpaBtn.disabled = false;
    }
}

function displayCgpaResults(data) {
    cgpaResultsSection.style.display = "block";

    document.getElementById("cgpaScore").textContent = Number(data.cgpa).toFixed(2);
    document.getElementById("cgpaTotalCredits").textContent = data.totalCredits;
    document.getElementById("cgpaTotalPoints").textContent = Number(data.totalPoints).toFixed(2);
    document.getElementById("cgpaClassification").textContent = "🏆 " + data.classification;

    const breakdownBody = document.getElementById("cgpaBreakdownBody");
    breakdownBody.innerHTML = "";

    (data.semesters || []).forEach(semester => {
        const tr = document.createElement("tr");

        tr.innerHTML = `
            <td><strong>Semester ${semester.semester}</strong></td>
            <td><strong>${Number(semester.gpa).toFixed(2)}</strong></td>
            <td>${semester.credits}</td>
            <td>${Number(semester.points).toFixed(2)}</td>
        `;

        breakdownBody.appendChild(tr);
    });

    cgpaResultsSection.scrollIntoView({ behavior: "smooth", block: "start" });
}

// ==========================================
// SEMESTER HISTORY
// ==========================================
function initHistory() {
    if (syncToCgpaBtn) {
        syncToCgpaBtn.addEventListener("click", handleSyncHistoryToCgpa);
    }

    if (clearAllHistoryBtn) {
        clearAllHistoryBtn.addEventListener("click", handleClearAllHistory);
    }
}

async function handleSaveGpaToHistory() {
    if (!lastCalculatedGpaResult) {
        alert("Please calculate your GPA first before saving to history.");
        return;
    }

    const title =
        semesterSaveTitle.value.trim() ||
        `Semester ${savedHistoryRecords.length + 1}`;

    const dateStr = new Date().toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
        year: "numeric"
    });

    const record = {
        id: "sem_" + Date.now(),
        title,
        date: dateStr,
        gpa: lastCalculatedGpaResult.gpa,
        totalCredits: lastCalculatedGpaResult.totalCredits,
        totalCreditPoints: lastCalculatedGpaResult.totalCreditPoints,
        subjects: lastCalculatedGpaResult.subjects
    };

    saveGpaToHistoryBtn.disabled = true;
    saveGpaToHistoryBtn.textContent = "Saving...";

    try {
        const response = await fetch("/api/history", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(record)
        });

        if (!response.ok) {
            throw new Error("Failed to save record on server.");
        }

        alert(`✅ "${title}" successfully saved to your Semester History!`);

        const match = title.match(/(\d+)/);

        if (match) {
            semesterSaveTitle.value = `Semester ${Number(match[1]) + 1}`;
        }

    } catch (error) {
        console.error("Save history error:", error);
        saveToLocalStorage(record);
        alert(`✅ "${title}" saved to local storage!`);

    } finally {
        saveGpaToHistoryBtn.disabled = false;
        saveGpaToHistoryBtn.textContent = "💾 Save to Semester History";
    }
}

async function loadHistory() {
    historySummaryText.textContent = "Loading archives...";

    try {
        const response = await fetch("/api/history");

        if (!response.ok) {
            throw new Error("Could not fetch history.");
        }

        savedHistoryRecords = await response.json();

    } catch (error) {
        console.warn("Using local history storage:", error);
        savedHistoryRecords = getFromLocalStorage();
    }

    renderHistoryUI(savedHistoryRecords);
}

function renderHistoryUI(records) {
    historyCardsContainer.innerHTML = "";

    if (!records || records.length === 0) {
        historyEmptyState.style.display = "block";
        historyCardsContainer.style.display = "none";
        historySummaryText.textContent = "0 saved semesters in database";
        return;
    }

    historyEmptyState.style.display = "none";
    historyCardsContainer.style.display = "grid";

    let totalCredits = 0;
    let totalPoints = 0;

    records.forEach(record => {
        totalCredits += Number(record.totalCredits) || 0;

        totalPoints +=
            Number(record.totalCreditPoints) ||
            ((Number(record.gpa) || 0) * (Number(record.totalCredits) || 0));
    });

    const averageCgpa =
        totalCredits > 0 ? (totalPoints / totalCredits).toFixed(2) : "0.00";

    historySummaryText.textContent =
        `${records.length} Semester(s) Saved • Overall CGPA: ${averageCgpa} • Total Credits: ${totalCredits}`;

    records.forEach(item => {
        const card = document.createElement("div");
        card.className = "history-card";

        const subjectTags = (item.subjects || []).map(subject =>
            `<span class="sub-tag">${escapeHtml(subject.name)} (${escapeHtml(subject.grade)})</span>`
        ).join("");

        card.innerHTML = `
            <div class="history-card-header">
                <div>
                    <div class="history-card-title">${escapeHtml(item.title)}</div>
                    <div class="history-card-date">📅 ${escapeHtml(item.date || "Recorded")}</div>
                </div>
                <div class="history-gpa-badge">${Number(item.gpa).toFixed(2)}</div>
            </div>

            <div class="history-stats">
                <span>Credits: <strong>${item.totalCredits}</strong></span>
                <span>Points: <strong>${item.totalCreditPoints}</strong></span>
            </div>

            <div class="history-subjects-list">${subjectTags}</div>

            <div class="history-card-actions">
                <button type="button" class="btn btn-secondary btn-sm load-record-btn" style="flex:1;">
                    👁️ Load in GPA
                </button>
                <button type="button" class="btn btn-delete delete-record-btn" title="Delete record">
                    🗑️
                </button>
            </div>
        `;

        card.querySelector(".load-record-btn").addEventListener("click", () => {
            loadSemesterIntoGpa(item);
        });

        card.querySelector(".delete-record-btn").addEventListener("click", () => {
            handleDeleteHistoryRecord(item.id);
        });

        historyCardsContainer.appendChild(card);
    });
}

function loadSemesterIntoGpa(item) {
    if (!item.subjects || item.subjects.length === 0) {
        alert("This record doesn't contain subject details.");
        return;
    }

    subjectsBody.innerHTML = "";

    item.subjects.forEach(subject => {
        addCourseRow(subject.name, subject.credit, subject.grade);
    });

    semesterSaveTitle.value = item.title;
    switchTab("gpa");

    alert(`Loaded "${item.title}" into the Semester GPA Calculator!`);
}

async function handleDeleteHistoryRecord(id) {
    if (!confirm("Are you sure you want to delete this semester record?")) {
        return;
    }

    try {
        const response = await fetch(
            `/api/history?id=${encodeURIComponent(id)}`,
            { method: "DELETE" }
        );

        if (!response.ok) {
            throw new Error("Server delete failed.");
        }

    } catch (error) {
        deleteFromLocalStorage(id);
    }

    loadHistory();
}

async function handleClearAllHistory() {
    if (savedHistoryRecords.length === 0) {
        alert("History is already empty.");
        return;
    }

    if (!confirm("Are you sure you want to delete ALL saved semester records?")) {
        return;
    }

    try {
        const response = await fetch("/api/history", { method: "DELETE" });

        if (!response.ok) {
            throw new Error("Server clear failed.");
        }

    } catch (error) {
        localStorage.removeItem("gpa_history");
    }

    loadHistory();
}

function handleSyncHistoryToCgpa() {
    if (savedHistoryRecords.length === 0) {
        alert("No saved semesters found. Save some semesters first!");
        return;
    }

    semestersBody.innerHTML = "";

    savedHistoryRecords.forEach((record, index) => {
        addSemesterRow(
            record.title || `Semester ${index + 1}`,
            record.gpa,
            record.totalCredits
        );
    });

    switchTab("cgpa");
    handleCgpaFormSubmit();
}

function getFromLocalStorage() {
    try {
        return JSON.parse(localStorage.getItem("gpa_history") || "[]");
    } catch {
        return [];
    }
}

function saveToLocalStorage(record) {
    const records = getFromLocalStorage();
    records.unshift(record);
    localStorage.setItem("gpa_history", JSON.stringify(records));
}

function deleteFromLocalStorage(id) {
    const records = getFromLocalStorage().filter(record => record.id !== id);
    localStorage.setItem("gpa_history", JSON.stringify(records));
}

// ==========================================
// MARKSHEET UPLOAD
// ==========================================
function initUpload() {
    if (!browseFilesBtn || !marksheetFileInput || !marksheetDropzone) {
        console.error("Upload controls were not found in index.html.");
        return;
    }

    browseFilesBtn.addEventListener("click", () => marksheetFileInput.click());
    marksheetFileInput.addEventListener("change", handleFileInputChange);

    ["dragenter", "dragover"].forEach(eventName => {
        marksheetDropzone.addEventListener(eventName, event => {
            event.preventDefault();
            marksheetDropzone.classList.add("dragover");
        });
    });

    ["dragleave", "drop"].forEach(eventName => {
        marksheetDropzone.addEventListener(eventName, event => {
            event.preventDefault();
            marksheetDropzone.classList.remove("dragover");
        });
    });

    marksheetDropzone.addEventListener("drop", event => {
        const files = event.dataTransfer.files;

        if (files && files.length > 0) {
            handleSelectedFile(files[0]);
        }
    });

    if (cancelFileBtn) cancelFileBtn.addEventListener("click", resetUploadState);
    if (uploadFileBtn) uploadFileBtn.addEventListener("click", executeFileUpload);
    if (clearExtractionBtn) clearExtractionBtn.addEventListener("click", clearExtraction);
    if (useExtractedDataBtn) useExtractedDataBtn.addEventListener("click", useExtractedData);
}

function handleFileInputChange(event) {
    if (event.target.files && event.target.files.length > 0) {
        handleSelectedFile(event.target.files[0]);
    }
}

function handleSelectedFile(file) {
    const validExtensions = [".pdf", ".png", ".jpg", ".jpeg"];
    const extension = file.name.substring(file.name.lastIndexOf(".")).toLowerCase();

    if (!validExtensions.includes(extension)) {
        alert("Please select a valid PDF, JPG, or PNG marksheet.");
        return;
    }

    if (file.size > 10 * 1024 * 1024) {
        alert("File size exceeds the 10MB limit.");
        return;
    }

    selectedUploadFile = file;

    previewFileName.textContent = file.name;
    previewIcon.textContent = extension === ".pdf" ? "📄" : "🖼️";

    const sizeKb = Math.max(1, Math.round(file.size / 1024));

    previewFileMeta.textContent =
        `${extension.toUpperCase().replace(".", "")} Document • ${sizeKb} KB`;

    uploadPreviewCard.style.display = "flex";
    uploadSuccessCard.style.display = "none";
    uploadProgressContainer.style.display = "none";
    extractionCard.style.display = "none";

    extractedSubjectsBody.innerHTML = "";
    extractedTextBox.style.display = "none";
}

function resetUploadState() {
    selectedUploadFile = null;
    marksheetFileInput.value = "";

    uploadPreviewCard.style.display = "none";
    uploadProgressContainer.style.display = "none";
}

async function executeFileUpload() {
    if (!selectedUploadFile) {
        alert("Please select a file first.");
        return;
    }

    uploadFileBtn.disabled = true;
    cancelFileBtn.disabled = true;

    uploadProgressContainer.style.display = "block";
    uploadProgressBar.style.width = "20%";
    uploadProgressText.textContent = "Reading document...";

    try {
        const base64Data = await readFileAsBase64(selectedUploadFile);

        uploadProgressBar.style.width = "50%";
        uploadProgressText.textContent = "Uploading to Java server...";

        const payload = {
            fileName: selectedUploadFile.name,
            fileData: base64Data
        };

        const response = await fetch("/api/upload", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        });

        const responseText = await response.text();

        console.log("Upload HTTP status:", response.status);
        console.log("Upload response:", responseText);

        let result;

        try {
            result = JSON.parse(responseText);
        } catch (error) {
            console.error("Upload JSON parsing error:", error);

            throw new Error(
                "The server response is not valid JSON. Check the Network tab for the raw response."
            );
        }

        console.log("Parsed upload result:", result);

        if (!response.ok) {
            throw new Error(
                result.message ||
                result.error ||
                `Server returned HTTP ${response.status}`
            );
        }

        if (result.status !== "SUCCESS") {
            throw new Error(
                result.message ||
                result.error ||
                "The server could not process the uploaded marksheet."
            );
        }

        uploadedFileRecord = result;

        renderExtractedData(
            result.extractedSubjects || [],
            result.extractedText || ""
        );

        uploadProgressBar.style.width = "100%";
        uploadProgressText.textContent = "Upload complete!";

        setTimeout(() => {
            uploadPreviewCard.style.display = "none";
            uploadProgressContainer.style.display = "none";
            uploadSuccessCard.style.display = "flex";

            uploadTimestamp.textContent = new Date().toLocaleTimeString();

            uploadSuccessDetails.textContent =
                `File "${result.fileName || selectedUploadFile.name}" ` +
                `(${result.fileSize || "Size unavailable"}) uploaded successfully.` +
                (result.filePath ? ` Saved at: ${result.filePath}` : "");

        }, 500);

    } catch (error) {
        console.error("Upload error:", error);

        uploadProgressContainer.style.display = "none";

        alert(
            "Upload failed.\n\nReason: " +
            (error.message || "Unknown error")
        );

    } finally {
        uploadFileBtn.disabled = false;
        cancelFileBtn.disabled = false;
    }
}

function readFileAsBase64(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();

        reader.onload = () => {
            if (typeof reader.result !== "string") {
                reject(new Error("The browser could not read the selected file."));
                return;
            }

            // The server expects the complete Data URL or Base64 payload.
            resolve(reader.result);
        };

        reader.onerror = () => {
            reject(new Error("Failed to read the selected file."));
        };

        reader.readAsDataURL(file);
    });
}

// ==========================================
// EXTRACTED DATA REVIEW
// ==========================================
function renderExtractedData(subjects, text) {
    extractedSubjectsBody.innerHTML = "";

    if (!Array.isArray(subjects) || subjects.length === 0) {
        extractedSubjectsBody.innerHTML = `
            <tr>
                <td colspan="4" style="text-align:center; padding:24px;">
                    No subject rows were detected. This PDF may be image-based or use a different layout.
                </td>
            </tr>`;
    } else {
        subjects.forEach((item, index) => {
            const tr = document.createElement("tr");

            const credit = Number(item.credit);

            tr.innerHTML = `
                <td>${index + 1}</td>

                <td>
                    <input
                        type="text"
                        class="extracted-name"
                        value="${escapeHtml(item.name || "")}"
                        required
                    />
                </td>

                <td>
                    <input
                        type="number"
                        class="extracted-credit"
                        min="0"
                        max="12"
                        step="1"
                        value="${Number.isInteger(credit) ? credit : ""}"
                        required
                    />
                </td>

                <td>
                    <select class="extracted-grade" required>
                        <option value="">Select</option>
                        ${GRADES.map(grade =>
                            `<option value="${grade}" ${grade === item.grade ? "selected" : ""}>${grade}</option>`
                        ).join("")}
                    </select>
                </td>
            `;

            extractedSubjectsBody.appendChild(tr);
        });
    }

    if (text && text.trim()) {
        extractedText.textContent = text;
        extractedTextBox.style.display = "block";
    } else {
        extractedText.textContent = "";
        extractedTextBox.style.display = "none";
    }

    extractionCard.style.display = "block";
}

function clearExtraction() {
    extractionCard.style.display = "none";
    extractedSubjectsBody.innerHTML = "";
    extractedText.textContent = "";
    extractedTextBox.style.display = "none";
    uploadSuccessCard.style.display = "none";
    uploadedFileRecord = null;
}

function useExtractedData() {
    const rows = extractedSubjectsBody.querySelectorAll("tr");
    const extracted = [];

    for (const row of rows) {
        const nameElement = row.querySelector(".extracted-name");
        const creditElement = row.querySelector(".extracted-credit");
        const gradeElement = row.querySelector(".extracted-grade");

        // Ignore the informational row shown when no subjects were detected.
        if (!nameElement || !creditElement || !gradeElement) {
            continue;
        }

        const name = nameElement.value.trim();
        const creditValue = creditElement.value;
        const credit = Number(creditValue);
        const grade = gradeElement.value;

        if (!name) {
            alert("Please enter a name for every subject.");
            return;
        }

        if (
            creditValue === "" ||
            !Number.isInteger(credit) ||
            credit < 0 ||
            credit > 12
        ) {
            alert(`Enter valid credits between 0 and 12 for "${name}".`);
            return;
        }

        if (!grade) {
            alert(`Please select a grade for "${name}".`);
            return;
        }

        // Keep zero-credit subjects. They must not contribute to GPA.
        extracted.push({ name, credit, grade });
    }

    if (extracted.length === 0) {
        alert("No valid extracted subject rows are available.");
        return;
    }

    if (!extracted.some(subject => subject.credit > 0)) {
        alert("At least one subject must have credits greater than zero.");
        return;
    }

    subjectsBody.innerHTML = "";

    extracted.forEach(subject => {
        addCourseRow(subject.name, subject.credit, subject.grade);
    });

    resultsSection.style.display = "none";
    lastCalculatedGpaResult = null;
    semesterSaveTitle.value = "";

    switchTab("gpa");
}

// ==========================================
// UTILITIES
// ==========================================
function escapeHtml(value) {
    if (value === null || value === undefined) {
        return "";
    }

    return String(value).replace(/[&<>"']/g, character => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[character]);
}