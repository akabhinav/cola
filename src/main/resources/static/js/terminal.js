/**
 * AI-Powered Terminal Integration with XTerm.js
 *
 * Features:
 * - Full terminal emulation with XTerm.js
 * - AI command suggestions
 * - Natural language to shell command translation
 * - Command history and workflows
 * - Real-time command execution
 */

let term = null;
let fitAddon = null;
let currentProjectId = null;
let sessionId = null;
let commandHistory = [];
let historyIndex = -1;
let currentCommand = '';

/**
 * Initialize XTerm.js terminal
 */
function initTerminal() {
    // Create terminal instance
    term = new Terminal({
        cursorBlink: true,
        fontSize: 14,
        fontFamily: 'Menlo, Monaco, "Courier New", monospace',
        theme: {
            background: '#1e1e1e',
            foreground: '#d4d4d4',
            cursor: '#007acc',
            selection: '#264f78',
            black: '#000000',
            red: '#cd3131',
            green: '#0dbc79',
            yellow: '#e5e510',
            blue: '#2472c8',
            magenta: '#bc3fbc',
            cyan: '#11a8cd',
            white: '#e5e5e5',
            brightBlack: '#666666',
            brightRed: '#f14c4c',
            brightGreen: '#23d18b',
            brightYellow: '#f5f543',
            brightBlue: '#3b8eea',
            brightMagenta: '#d670d6',
            brightCyan: '#29b8db',
            brightWhite: '#e5e5e5'
        },
        allowProposedApi: true
    });

    // Add fit addon for responsive sizing
    fitAddon = new FitAddon.FitAddon();
    term.loadAddon(fitAddon);

    // Open terminal in DOM
    term.open(document.getElementById('xterm'));
    fitAddon.fit();

    // Welcome message
    term.writeln('\x1b[1;32m╔═══════════════════════════════════════════════╗\x1b[0m');
    term.writeln('\x1b[1;32m║  🤖 COLA AI-Powered Terminal                 ║\x1b[0m');
    term.writeln('\x1b[1;32m╚═══════════════════════════════════════════════╝\x1b[0m');
    term.writeln('');
    term.writeln('\x1b[1;36mFeatures:\x1b[0m');
    term.writeln('  • \x1b[1mCtrl+K\x1b[0m - Natural language commands');
    term.writeln('  • \x1b[1mTab\x1b[0m - AI-powered suggestions');
    term.writeln('  • \x1b[1m↑/↓\x1b[0m - Command history');
    term.writeln('');

    writePrompt();

    // Setup input handling
    setupTerminalInput();

    // Setup UI handlers
    setupUIHandlers();

    // Load quick commands
    loadQuickCommands();

    // Setup window resize handler
    window.addEventListener('resize', () => {
        if (fitAddon) fitAddon.fit();
    });

    console.log('AI Terminal initialized');
}

/**
 * Setup terminal input handling
 */
function setupTerminalInput() {
    term.onData(data => {
        const code = data.charCodeAt(0);

        // Enter key
        if (code === 13) {
            term.writeln('');
            executeCurrentCommand();
            return;
        }

        // Backspace
        if (code === 127) {
            if (currentCommand.length > 0) {
                currentCommand = currentCommand.slice(0, -1);
                term.write('\b \b');
            }
            return;
        }

        // Ctrl+C
        if (code === 3) {
            term.writeln('^C');
            currentCommand = '';
            writePrompt();
            return;
        }

        // Ctrl+K - Natural language input
        if (code === 11) {
            showNaturalLanguageInput();
            return;
        }

        // Tab - Suggestions
        if (code === 9) {
            showAISuggestions();
            return;
        }

        // Arrow up - Previous command
        if (data === '\x1b[A') {
            navigateHistory(-1);
            return;
        }

        // Arrow down - Next command
        if (data === '\x1b[B') {
            navigateHistory(1);
            return;
        }

        // Regular character
        if (code >= 32 && code <= 126) {
            currentCommand += data;
            term.write(data);
        }
    });
}

/**
 * Execute the current command
 */
async function executeCurrentCommand() {
    const command = currentCommand.trim();
    currentCommand = '';

    if (!command) {
        writePrompt();
        return;
    }

    // Add to history
    commandHistory.push(command);
    historyIndex = commandHistory.length;

    // Execute command
    await executeCommand(command);

    writePrompt();
}

/**
 * Execute a shell command
 */
async function executeCommand(command) {
    if (!currentProjectId) {
        term.writeln('\x1b[1;31mError: No project selected\x1b[0m');
        return;
    }

    try {
        const response = await fetch('/api/v1/terminal/execute', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                projectId: currentProjectId,
                command: command,
                sessionId: sessionId,
                streaming: false,
                timeoutMs: 30000
            })
        });

        const data = await response.json();

        if (data.success) {
            // Write stdout
            if (data.stdout) {
                term.writeln(data.stdout);
            }
        } else {
            // Write error
            term.writeln('\x1b[1;31m' + (data.stderr || data.errorMessage) + '\x1b[0m');
        }

        // Update session ID
        if (data.sessionId) {
            sessionId = data.sessionId;
        }

        // Refresh suggestions based on command
        refreshSuggestions();

    } catch (error) {
        term.writeln('\x1b[1;31mError executing command: ' + error.message + '\x1b[0m');
    }
}

/**
 * Write command prompt
 */
function writePrompt() {
    term.write('\x1b[1;32m➜\x1b[0m \x1b[1;34m~\x1b[0m ');
}

/**
 * Navigate command history
 */
function navigateHistory(direction) {
    const newIndex = historyIndex + direction;

    if (newIndex < 0 || newIndex > commandHistory.length) {
        return;
    }

    // Clear current line
    term.write('\r\x1b[K');
    writePrompt();

    historyIndex = newIndex;

    if (historyIndex < commandHistory.length) {
        const command = commandHistory[historyIndex];
        currentCommand = command;
        term.write(command);
    } else {
        currentCommand = '';
    }
}

/**
 * Show natural language input modal
 */
function showNaturalLanguageInput() {
    const modal = document.getElementById('nlCommandInput');
    const input = document.getElementById('nlInput');

    modal.style.display = 'block';
    input.focus();

    term.writeln('');
    term.writeln('\x1b[1;36m✨ Natural Language Mode\x1b[0m');
    writePrompt();
}

/**
 * Show AI suggestions
 */
async function showAISuggestions() {
    if (!currentProjectId) return;

    try {
        const response = await fetch(
            `/api/v1/terminal/suggestions?projectId=${currentProjectId}&partial=${encodeURIComponent(currentCommand)}`
        );

        const suggestions = await response.json();

        if (suggestions.length > 0) {
            term.writeln('');
            term.writeln('\x1b[1;36m💡 AI Suggestions:\x1b[0m');

            suggestions.slice(0, 5).forEach((suggestion, index) => {
                term.writeln(
                    `  \x1b[1m${index + 1}.\x1b[0m ${suggestion.command} - \x1b[2m${suggestion.description}\x1b[0m`
                );
            });

            writePrompt();
            term.write(currentCommand);
        }

    } catch (error) {
        console.error('Error getting suggestions:', error);
    }
}

/**
 * Setup UI event handlers
 */
function setupUIHandlers() {
    // Natural language command button
    document.getElementById('nlCommandBtn').addEventListener('click', () => {
        showNaturalLanguageInput();
    });

    // Close NL input
    document.querySelector('.close-nl-input').addEventListener('click', () => {
        document.getElementById('nlCommandInput').style.display = 'none';
    });

    // Translate button
    document.getElementById('translateBtn').addEventListener('click', async () => {
        await translateNaturalLanguage();
    });

    // Enter key in NL input
    document.getElementById('nlInput').addEventListener('keypress', async (e) => {
        if (e.key === 'Enter') {
            await translateNaturalLanguage();
        }
    });

    // Command suggestions button
    document.getElementById('commandSuggestionsBtn').addEventListener('click', () => {
        refreshSuggestions();
    });

    // Clear terminal
    document.getElementById('clearTerminal').addEventListener('click', () => {
        term.clear();
        writePrompt();
    });
}

/**
 * Translate natural language to command
 */
async function translateNaturalLanguage() {
    const input = document.getElementById('nlInput');
    const query = input.value.trim();

    if (!query) return;

    try {
        term.writeln('\x1b[1;36mTranslating: "' + query + '"\x1b[0m');

        const response = await fetch('/api/v1/terminal/translate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                projectId: currentProjectId,
                query: query,
                shell: 'bash',
                os: 'linux'
            })
        });

        const data = await response.json();

        if (data.command) {
            term.writeln('');
            term.writeln('\x1b[1;32m✓ Command:\x1b[0m ' + data.command);
            term.writeln('\x1b[2m' + data.explanation + '\x1b[0m');

            if (data.warnings && data.warnings.length > 0) {
                term.writeln('');
                term.writeln('\x1b[1;33m⚠ Warnings:\x1b[0m');
                data.warnings.forEach(warning => {
                    term.writeln('  • ' + warning);
                });
            }

            term.writeln('');
            term.writeln('\x1b[1;36mPress Enter to execute or modify the command\x1b[0m');

            // Set as current command
            currentCommand = data.command;
            writePrompt();
            term.write(data.command);

            // Hide modal
            document.getElementById('nlCommandInput').style.display = 'none';
            input.value = '';
        }

    } catch (error) {
        term.writeln('\x1b[1;31mTranslation error: ' + error.message + '\x1b[0m');
    }
}

/**
 * Load quick commands
 */
async function loadQuickCommands() {
    try {
        const response = await fetch('/api/v1/terminal/quick-commands');
        const commands = await response.json();

        const container = document.getElementById('quickCommands');
        container.innerHTML = '';

        commands.forEach(cmd => {
            const item = document.createElement('div');
            item.className = 'quick-command-item';
            item.innerHTML = `
                <div class="command-text">${cmd.command}</div>
                <div class="command-desc">${cmd.explanation}</div>
            `;
            item.addEventListener('click', () => {
                currentCommand = cmd.command;
                term.write('\r\x1b[K');
                writePrompt();
                term.write(cmd.command);
            });
            container.appendChild(item);
        });

    } catch (error) {
        console.error('Error loading quick commands:', error);
    }
}

/**
 * Refresh AI suggestions
 */
async function refreshSuggestions() {
    if (!currentProjectId) return;

    try {
        const response = await fetch(
            `/api/v1/terminal/contextual-suggestions/${currentProjectId}` +
            (sessionId ? `?sessionId=${sessionId}` : '')
        );

        const suggestions = await response.json();

        const container = document.getElementById('commandSuggestions');
        container.innerHTML = '';

        suggestions.slice(0, 5).forEach(suggestion => {
            const item = document.createElement('div');
            item.className = 'suggestion-item';
            item.innerHTML = `
                <div class="suggestion-cmd">${suggestion.command}</div>
                <div class="suggestion-desc">${suggestion.description}</div>
                <div class="suggestion-confidence">${Math.round(suggestion.confidence * 100)}%</div>
            `;
            item.addEventListener('click', () => {
                currentCommand = suggestion.command;
                term.write('\r\x1b[K');
                writePrompt();
                term.write(suggestion.command);
            });
            container.appendChild(item);
        });

    } catch (error) {
        console.error('Error refreshing suggestions:', error);
    }
}

/**
 * Set current project for terminal
 */
function setTerminalProject(projectId) {
    currentProjectId = projectId;
    sessionId = null; // Reset session
    loadQuickCommands();
    refreshSuggestions();

    term.writeln('');
    term.writeln('\x1b[1;32m✓ Project context updated\x1b[0m');
    writePrompt();
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initTerminal);
} else {
    initTerminal();
}

// Export functions
window.Terminal = {
    setProject: setTerminalProject
};
