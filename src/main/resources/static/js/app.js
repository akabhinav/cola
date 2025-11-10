// COLA Agent Platform - Main Application Script

class ColaApp {
    constructor() {
        this.API_BASE = '/api/v1';
        this.currentUserId = this.generateUserId(); // In production, get from auth
        this.currentProjectId = null;
        this.stompClient = null;
        this.projects = [];

        this.init();
    }

    init() {
        this.setupEventListeners();
        this.setupWebSocket();
        this.loadProjects();
        this.updateDashboardStats();
    }

    generateUserId() {
        // For development - generate or retrieve from localStorage
        let userId = localStorage.getItem('cola_user_id');
        if (!userId) {
            userId = '00000000-0000-0000-0000-000000000000'; // Default user
            localStorage.setItem('cola_user_id', userId);
        }
        return userId;
    }

    setupEventListeners() {
        // Navigation
        document.querySelectorAll('.nav-item').forEach(item => {
            item.addEventListener('click', (e) => this.handleNavigation(e));
        });

        // Modal controls
        const modal = document.getElementById('createProjectModal');
        const newProjectBtn = document.getElementById('newProjectBtn');
        const createProjectBtn = document.getElementById('createProjectBtn');
        const closeModal = document.getElementById('closeModal');
        const cancelBtn = document.getElementById('cancelBtn');

        newProjectBtn.addEventListener('click', () => this.showModal());
        createProjectBtn.addEventListener('click', () => this.showModal());
        closeModal.addEventListener('click', () => this.hideModal());
        cancelBtn.addEventListener('click', () => this.hideModal());

        // Project form submission
        document.getElementById('createProjectForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.createProject();
        });

        // Chat
        const chatInput = document.getElementById('chatInput');
        const sendBtn = document.getElementById('sendBtn');

        sendBtn.addEventListener('click', () => this.sendMessage());
        chatInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.sendMessage();
            }
        });

        // Auto-resize chat input
        chatInput.addEventListener('input', function() {
            this.style.height = 'auto';
            this.style.height = (this.scrollHeight) + 'px';
        });

        // Console controls
        document.getElementById('clearConsole').addEventListener('click', () => this.clearConsole());
        document.getElementById('exportLogs').addEventListener('click', () => this.exportLogs());

        // Modal backdrop click
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                this.hideModal();
            }
        });
    }

    setupWebSocket() {
        try {
            const socket = new SockJS('/ws');
            this.stompClient = Stomp.over(socket);

            this.stompClient.connect({}, (frame) => {
                console.log('WebSocket connected:', frame);
                this.updateAgentStatus('Connected');

                // Subscribe to message topics
                this.stompClient.subscribe('/topic/messages', (message) => {
                    this.handleWebSocketMessage(JSON.parse(message.body));
                });

                // Subscribe to user-specific queue for code generation
                this.stompClient.subscribe(`/user/${this.currentUserId}/queue/code`, (message) => {
                    this.handleCodeChunk(message.body);
                });

            }, (error) => {
                console.error('WebSocket error:', error);
                this.updateAgentStatus('Disconnected');
                // Retry connection after 5 seconds
                setTimeout(() => this.setupWebSocket(), 5000);
            });
        } catch (error) {
            console.error('Failed to setup WebSocket:', error);
            this.updateAgentStatus('Error');
        }
    }

    handleNavigation(e) {
        const view = e.currentTarget.dataset.view;

        // Update active nav item
        document.querySelectorAll('.nav-item').forEach(item => {
            item.classList.remove('active');
        });
        e.currentTarget.classList.add('active');

        // Show corresponding view
        document.querySelectorAll('.view').forEach(v => {
            v.classList.remove('active');
        });
        document.getElementById(`${view}View`).classList.add('active');
    }

    showModal() {
        document.getElementById('createProjectModal').classList.add('active');
    }

    hideModal() {
        document.getElementById('createProjectModal').classList.remove('active');
        document.getElementById('createProjectForm').reset();
    }

    async createProject() {
        const projectData = {
            name: document.getElementById('projectNameInput').value,
            description: document.getElementById('projectDescInput').value,
            language: document.getElementById('projectLangSelect').value,
            framework: document.getElementById('projectFrameworkInput').value
        };

        try {
            const response = await fetch(`${this.API_BASE}/projects`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-User-Id': this.currentUserId
                },
                body: JSON.stringify(projectData)
            });

            if (!response.ok) {
                throw new Error('Failed to create project');
            }

            const result = await response.json();
            this.logToConsole('success', `Project "${result.data.name}" created successfully`);
            this.hideModal();
            await this.loadProjects();
            this.updateDashboardStats();

        } catch (error) {
            console.error('Error creating project:', error);
            this.logToConsole('error', `Failed to create project: ${error.message}`);
            alert('Failed to create project. Please try again.');
        }
    }

    async loadProjects() {
        try {
            const response = await fetch(`${this.API_BASE}/projects`, {
                headers: {
                    'X-User-Id': this.currentUserId
                }
            });

            if (!response.ok) {
                throw new Error('Failed to load projects');
            }

            const result = await response.json();
            this.projects = result.data || [];
            this.renderProjects();

        } catch (error) {
            console.error('Error loading projects:', error);
            this.logToConsole('error', `Failed to load projects: ${error.message}`);
        }
    }

    renderProjects() {
        const recentContainer = document.getElementById('recentProjects');
        const projectsContainer = document.getElementById('projectsList');

        if (this.projects.length === 0) {
            const emptyMessage = '<p class="text-secondary">No projects yet. Create your first project to get started!</p>';
            recentContainer.innerHTML = emptyMessage;
            projectsContainer.innerHTML = emptyMessage;
            return;
        }

        const projectHTML = this.projects.map(project => `
            <div class="project-card" onclick="colaApp.selectProject('${project.id}')">
                <h3>${project.name}</h3>
                <p>${project.description || 'No description'}</p>
                <div class="project-meta">
                    <span class="project-badge">${project.language}</span>
                    ${project.framework ? `<span class="project-badge">${project.framework}</span>` : ''}
                    <span class="project-badge">${project.status}</span>
                </div>
            </div>
        `).join('');

        // Show recent 6 projects in dashboard
        const recentHTML = this.projects.slice(0, 6).map(project => `
            <div class="project-card" onclick="colaApp.selectProject('${project.id}')">
                <h3>${project.name}</h3>
                <p>${project.description || 'No description'}</p>
                <div class="project-meta">
                    <span class="project-badge">${project.language}</span>
                    <span class="project-badge">${project.status}</span>
                </div>
            </div>
        `).join('');

        recentContainer.innerHTML = recentHTML;
        projectsContainer.innerHTML = projectHTML;
    }

    selectProject(projectId) {
        this.currentProjectId = projectId;
        const project = this.projects.find(p => p.id === projectId);
        if (project) {
            document.getElementById('projectName').textContent = project.name;
            this.logToConsole('info', `Switched to project: ${project.name}`);
        }
    }

    async sendMessage() {
        const input = document.getElementById('chatInput');
        const message = input.value.trim();

        if (!message) return;

        if (!this.currentProjectId) {
            alert('Please select a project first');
            return;
        }

        // Display user message
        this.addChatMessage(message, 'user');
        input.value = '';
        input.style.height = 'auto';

        // Send via WebSocket if connected, otherwise use REST API
        if (this.stompClient && this.stompClient.connected) {
            this.stompClient.send('/app/chat', {}, JSON.stringify({
                projectId: this.currentProjectId,
                message: message
            }));
        } else {
            // Fallback to REST API
            try {
                const response = await fetch(`${this.API_BASE}/agent/chat`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-User-Id': this.currentUserId
                    },
                    body: JSON.stringify({
                        projectId: this.currentProjectId,
                        message: message
                    })
                });

                if (!response.ok) {
                    throw new Error('Failed to send message');
                }

                const result = await response.json();
                this.addChatMessage(result.data.message, 'assistant');

            } catch (error) {
                console.error('Error sending message:', error);
                this.addChatMessage('Sorry, I encountered an error. Please try again.', 'assistant');
            }
        }
    }

    handleWebSocketMessage(data) {
        this.addChatMessage(data.message, data.role.toLowerCase());
    }

    handleCodeChunk(chunk) {
        if (chunk === '[DONE]') {
            this.logToConsole('success', 'Code generation completed');
            return;
        }
        // Append code chunk to code editor or console
        this.logToConsole('info', chunk);
    }

    addChatMessage(message, role) {
        const messagesContainer = document.getElementById('chatMessages');
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${role}`;

        messageDiv.innerHTML = `
            <div class="message-avatar">
                <i class="fas fa-${role === 'user' ? 'user' : 'robot'}"></i>
            </div>
            <div class="message-content">
                <p>${this.formatMessage(message)}</p>
            </div>
        `;

        messagesContainer.appendChild(messageDiv);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    formatMessage(message) {
        // Basic markdown-like formatting
        return message
            .replace(/```(.*?)```/gs, '<pre><code>$1</code></pre>')
            .replace(/`(.*?)`/g, '<code>$1</code>')
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/\*(.*?)\*/g, '<em>$1</em>')
            .replace(/\n/g, '<br>');
    }

    logToConsole(level, message) {
        const consoleOutput = document.getElementById('consoleOutput');
        const timestamp = new Date().toLocaleTimeString();
        const logEntry = document.createElement('div');
        logEntry.className = `log-entry ${level}`;
        logEntry.innerHTML = `
            <span class="timestamp">[${timestamp}]</span>
            <span class="message">${message}</span>
        `;
        consoleOutput.appendChild(logEntry);
        consoleOutput.scrollTop = consoleOutput.scrollHeight;
    }

    clearConsole() {
        document.getElementById('consoleOutput').innerHTML = '';
        this.logToConsole('info', 'Console cleared');
    }

    exportLogs() {
        const logs = document.getElementById('consoleOutput').innerText;
        const blob = new Blob([logs], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `cola-logs-${new Date().toISOString()}.txt`;
        a.click();
        URL.revokeObjectURL(url);
        this.logToConsole('info', 'Logs exported successfully');
    }

    updateDashboardStats() {
        document.getElementById('projectCount').textContent = this.projects.length;
        // These would be calculated from actual data
        document.getElementById('fileCount').textContent = '0';
        document.getElementById('successCount').textContent = '0';
        document.getElementById('deployCount').textContent = '0';
    }

    updateAgentStatus(status) {
        const statusElement = document.getElementById('agentStatus');
        statusElement.textContent = `Agent: ${status}`;

        const indicator = document.querySelector('.status-indicator');
        if (status === 'Connected' || status === 'Ready') {
            indicator.style.color = 'var(--success)';
        } else {
            indicator.style.color = 'var(--error)';
        }
    }
}

// Initialize the application when DOM is ready
let colaApp;
document.addEventListener('DOMContentLoaded', () => {
    colaApp = new ColaApp();
    console.log('COLA Agent Platform initialized');
});
