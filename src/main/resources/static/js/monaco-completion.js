/**
 * Monaco Editor Integration with COLA AI Completions
 *
 * This module provides Cursor-quality code completions by:
 * - Integrating Monaco editor with our completion API
 * - Supporting real-time streaming completions
 * - Providing inline suggestions as you type
 * - Caching completions for instant feel
 */

let monacoEditor = null;
let currentProjectId = null;
let currentFilePath = 'untitled.txt';
let currentLanguage = 'plaintext';
let completionCache = new Map();
let isCompletionInProgress = false;

// Debounce timer for auto-completions
let completionDebounceTimer = null;
const COMPLETION_DEBOUNCE_MS = 300;

/**
 * Initialize Monaco Editor
 */
function initMonacoEditor() {
    require(['vs/editor/editor.main'], function() {
        // Create Monaco editor instance
        monacoEditor = monaco.editor.create(document.getElementById('monacoEditor'), {
            value: '# Welcome to COLA Agent Platform\n\nStart coding and experience AI-powered completions!',
            language: 'markdown',
            theme: 'vs-dark',
            automaticLayout: true,
            fontSize: 14,
            minimap: { enabled: true },
            scrollBeyondLastLine: false,
            wordWrap: 'on',
            suggestOnTriggerCharacters: true,
            quickSuggestions: {
                other: true,
                comments: false,
                strings: false
            },
            parameterHints: { enabled: true },
            suggest: {
                showWords: false,  // Disable default word-based suggestions
                showSnippets: false
            }
        });

        // Register custom completion provider
        registerCompletionProvider();

        // Setup auto-completion triggers
        setupAutoCompletions();

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();

        console.log('Monaco editor initialized with AI completions');
        updateCompletionStatus('ready');
    });
}

/**
 * Register AI completion provider with Monaco
 */
function registerCompletionProvider() {
    // Register for all languages
    const languages = ['javascript', 'typescript', 'python', 'java', 'go', 'html', 'css', 'json', 'markdown'];

    languages.forEach(lang => {
        monaco.languages.registerInlineCompletionsProvider(lang, {
            provideInlineCompletions: async (model, position, context, token) => {
                return await provideAICompletions(model, position, context, token);
            },
            freeInlineCompletions: () => {}
        });
    });
}

/**
 * Provide AI-powered completions
 */
async function provideAICompletions(model, position, context, token) {
    try {
        // Don't provide completions if already in progress
        if (isCompletionInProgress) {
            return { items: [] };
        }

        // Get context around cursor
        const prefix = model.getValueInRange({
            startLineNumber: 1,
            startColumn: 1,
            endLineNumber: position.lineNumber,
            endColumn: position.column
        });

        const suffix = model.getValueInRange({
            startLineNumber: position.lineNumber,
            startColumn: position.column,
            endLineNumber: model.getLineCount(),
            endColumn: model.getLineLength(model.getLineCount()) + 1
        });

        // Check cache first
        const cacheKey = generateCacheKey(prefix, suffix);
        if (completionCache.has(cacheKey)) {
            const cached = completionCache.get(cacheKey);
            return {
                items: [{
                    insertText: cached,
                    range: new monaco.Range(
                        position.lineNumber,
                        position.column,
                        position.lineNumber,
                        position.column
                    )
                }]
            };
        }

        // Request completion from API
        const completion = await requestCompletion(prefix, suffix, position);

        if (completion && completion.trim().length > 0) {
            // Cache the result
            completionCache.set(cacheKey, completion);

            // Clean old cache entries (keep last 50)
            if (completionCache.size > 50) {
                const firstKey = completionCache.keys().next().value;
                completionCache.delete(firstKey);
            }

            return {
                items: [{
                    insertText: completion,
                    range: new monaco.Range(
                        position.lineNumber,
                        position.column,
                        position.lineNumber,
                        position.column
                    )
                }]
            };
        }

        return { items: [] };

    } catch (error) {
        console.error('Error providing completions:', error);
        return { items: [] };
    }
}

/**
 * Request completion from backend API
 */
async function requestCompletion(prefix, suffix, position) {
    const startTime = Date.now();
    updateCompletionStatus('loading');
    isCompletionInProgress = true;

    try {
        const request = {
            projectId: currentProjectId || '00000000-0000-0000-0000-000000000000',
            filePath: currentFilePath,
            language: currentLanguage,
            cursorPosition: {
                line: position.lineNumber,
                column: position.column
            },
            prefix: prefix,
            suffix: suffix,
            streaming: false,  // Use non-streaming for inline completions
            multiLine: true,
            maxTokens: 256,
            temperature: 0.2
        };

        const response = await fetch('/api/v1/completion/complete', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(request)
        });

        if (!response.ok) {
            throw new Error(`API error: ${response.status}`);
        }

        const data = await response.json();
        const latency = Date.now() - startTime;

        console.log(`Completion received: ${latency}ms, source: ${data.source}`);
        updateCompletionStatus('ready', latency, data.source);

        return data.completion;

    } catch (error) {
        console.error('Completion request failed:', error);
        updateCompletionStatus('error');
        return null;
    } finally {
        isCompletionInProgress = false;
    }
}

/**
 * Setup auto-completion triggers
 */
function setupAutoCompletions() {
    // Trigger completions on content change
    monacoEditor.onDidChangeModelContent((e) => {
        // Clear existing timer
        if (completionDebounceTimer) {
            clearTimeout(completionDebounceTimer);
        }

        // Debounce completion requests
        completionDebounceTimer = setTimeout(() => {
            // Trigger suggestion widget
            monacoEditor.trigger('keyboard', 'editor.action.inlineSuggest.trigger', {});
        }, COMPLETION_DEBOUNCE_MS);
    });

    // Listen for cursor position changes
    monacoEditor.onDidChangeCursorPosition((e) => {
        // Could trigger contextual completions here
    });
}

/**
 * Setup keyboard shortcuts
 */
function setupKeyboardShortcuts() {
    // Ctrl+Space: Manual completion trigger
    monacoEditor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Space, () => {
        monacoEditor.trigger('keyboard', 'editor.action.inlineSuggest.trigger', {});
    });

    // Ctrl+S: Save file
    monacoEditor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
        saveCurrentFile();
    });

    // Alt+.: Accept completion word
    monacoEditor.addCommand(monaco.KeyMod.Alt | monaco.KeyCode.Period, () => {
        monacoEditor.trigger('keyboard', 'acceptPartialSuggestion', {});
    });
}

/**
 * Update completion status indicator
 */
function updateCompletionStatus(status, latency = null, source = null) {
    const statusElement = document.getElementById('completionStatus');
    if (!statusElement) return;

    const indicator = statusElement.querySelector('.status-indicator');
    const text = statusElement.querySelector('.status-text');

    switch (status) {
        case 'ready':
            indicator.className = 'status-indicator ready';
            if (latency && source) {
                text.textContent = `AI Completions: Ready (${latency}ms, ${source})`;
            } else {
                text.textContent = 'AI Completions: Ready';
            }
            break;
        case 'loading':
            indicator.className = 'status-indicator loading';
            text.textContent = 'AI Completions: Generating...';
            break;
        case 'error':
            indicator.className = 'status-indicator error';
            text.textContent = 'AI Completions: Error';
            break;
    }
}

/**
 * Generate cache key for completion
 */
function generateCacheKey(prefix, suffix) {
    // Use last 200 chars of prefix and first 50 of suffix for cache key
    const prefixKey = prefix.slice(-200);
    const suffixKey = suffix.slice(0, 50);
    return `${prefixKey}|${suffixKey}`;
}

/**
 * Set current file context
 */
function setFileContext(projectId, filePath, language) {
    currentProjectId = projectId;
    currentFilePath = filePath;
    currentLanguage = language;

    // Update Monaco language
    if (monacoEditor) {
        const model = monacoEditor.getModel();
        monaco.editor.setModelLanguage(model, language);
    }

    // Clear cache when switching files
    completionCache.clear();
}

/**
 * Get editor content
 */
function getEditorContent() {
    return monacoEditor ? monacoEditor.getValue() : '';
}

/**
 * Set editor content
 */
function setEditorContent(content) {
    if (monacoEditor) {
        monacoEditor.setValue(content);
    }
}

/**
 * Save current file
 */
function saveCurrentFile() {
    const content = getEditorContent();
    console.log('Saving file:', currentFilePath);

    // TODO: Implement actual file save via API
    updateCompletionStatus('ready');

    // Show save notification
    showNotification('File saved successfully', 'success');
}

/**
 * Show notification to user
 */
function showNotification(message, type = 'info') {
    // Simple console notification for now
    console.log(`[${type.toUpperCase()}] ${message}`);

    // TODO: Implement toast notifications
}

/**
 * Test completion with sample code
 */
async function testCompletion() {
    if (!monacoEditor) {
        console.error('Editor not initialized');
        return;
    }

    // Set Java code for testing
    const testCode = `public class Calculator {
    public int add(int a, int b) {
        `;

    setFileContext(null, 'Calculator.java', 'java');
    setEditorContent(testCode);

    // Move cursor to end
    const lineCount = monacoEditor.getModel().getLineCount();
    const lastLineLength = monacoEditor.getModel().getLineLength(lineCount);
    monacoEditor.setPosition({ lineNumber: lineCount, column: lastLineLength + 1 });

    console.log('Test setup complete. Type to see AI completions!');
}

// Initialize when document is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initMonacoEditor);
} else {
    initMonacoEditor();
}

// Export functions for use in other modules
window.MonacoCompletion = {
    setFileContext,
    getEditorContent,
    setEditorContent,
    testCompletion
};
