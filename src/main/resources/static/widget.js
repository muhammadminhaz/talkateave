/**
 * Embeddable Chat Widget for TalkAtEve Bots
 * Usage: <script src="https://yourdomain.com/widget.js" data-bot-id="your-bot-id"></script>
 */

(function() {
    'use strict';

    // Get configuration from script tag
    const currentScript = document.currentScript || document.querySelector('script[data-bot-id]');
    const botId = currentScript?.getAttribute('data-bot-id');
    const apiUrl = currentScript?.getAttribute('data-api-url') || 'http://localhost:8080';
    const position = currentScript?.getAttribute('data-position') || 'bottom-right';
    const primaryColor = currentScript?.getAttribute('data-color') || '#3b82f6';

    if (!botId) {
        console.error('TalkAtEve Widget: data-bot-id attribute is required');
        return;
    }

    // Chat history storage (last 5 messages)
    let chatHistory = [];
    const MAX_HISTORY = 5;

    // Widget state
    let isOpen = false;
    let isLoading = false;

    // Create widget HTML
    function createWidget() {
        const widgetContainer = document.createElement('div');
        widgetContainer.id = 'talkateave-widget';
        widgetContainer.className = `talkateave-widget ${position}`;

        widgetContainer.innerHTML = `
            <style>
                /* Widget Container */
                .talkateave-widget {
                    position: fixed;
                    z-index: 9999;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                }
                
                .talkateave-widget.bottom-right {
                    bottom: 20px;
                    right: 20px;
                }
                
                .talkateave-widget.bottom-left {
                    bottom: 20px;
                    left: 20px;
                }
                
                .talkateave-widget.top-right {
                    top: 20px;
                    right: 20px;
                }
                
                .talkateave-widget.top-left {
                    top: 20px;
                    left: 20px;
                }

                /* Chat Button */
                #talkateave-chat-button {
                    width: 60px;
                    height: 60px;
                    border-radius: 50%;
                    background: ${primaryColor};
                    border: none;
                    cursor: pointer;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    transition: transform 0.2s, box-shadow 0.2s;
                }
                
                #talkateave-chat-button:hover {
                    transform: scale(1.1);
                    box-shadow: 0 6px 16px rgba(0,0,0,0.2);
                }
                
                #talkateave-chat-button svg {
                    width: 28px;
                    height: 28px;
                    fill: white;
                }

                /* Chat Window */
                #talkateave-chat-window {
                    position: absolute;
                    bottom: 80px;
                    right: 0;
                    width: 380px;
                    max-width: calc(100vw - 40px);
                    height: 500px;
                    max-height: calc(100vh - 120px);
                    background: white;
                    border-radius: 16px;
                    box-shadow: 0 8px 24px rgba(0,0,0,0.15);
                    display: none;
                    flex-direction: column;
                    overflow: hidden;
                }
                
                #talkateave-chat-window.open {
                    display: flex;
                    animation: slideUp 0.3s ease-out;
                }
                
                @keyframes slideUp {
                    from {
                        opacity: 0;
                        transform: translateY(20px);
                    }
                    to {
                        opacity: 1;
                        transform: translateY(0);
                    }
                }

                /* Chat Header */
                #talkateave-chat-header {
                    background: ${primaryColor};
                    color: white;
                    padding: 16px;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                }
                
                #talkateave-chat-header h3 {
                    margin: 0;
                    font-size: 16px;
                    font-weight: 600;
                }
                
                #talkateave-close-button {
                    background: transparent;
                    border: none;
                    color: white;
                    cursor: pointer;
                    padding: 4px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }

                /* Messages Container */
                #talkateave-messages {
                    flex: 1;
                    overflow-y: auto;
                    padding: 16px;
                    background: #f9fafb;
                }
                
                .talkateave-message {
                    margin-bottom: 12px;
                    display: flex;
                    gap: 8px;
                }
                
                .talkateave-message.user {
                    flex-direction: row-reverse;
                }
                
                .talkateave-message-content {
                    max-width: 75%;
                    padding: 10px 14px;
                    border-radius: 12px;
                    font-size: 14px;
                    line-height: 1.5;
                    word-wrap: break-word;
                }
                
                .talkateave-message.bot .talkateave-message-content {
                    background: white;
                    color: #1f2937;
                    border: 1px solid #e5e7eb;
                }
                
                .talkateave-message.user .talkateave-message-content {
                    background: ${primaryColor};
                    color: white;
                }
                
                .talkateave-avatar {
                    width: 32px;
                    height: 32px;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 18px;
                    flex-shrink: 0;
                }
                
                .talkateave-message.bot .talkateave-avatar {
                    background: ${primaryColor}20;
                }
                
                .talkateave-message.user .talkateave-avatar {
                    background: ${primaryColor};
                    color: white;
                    font-size: 14px;
                }

                /* Typing Indicator */
                .talkateave-typing {
                    display: flex;
                    gap: 4px;
                    padding: 10px 14px;
                    background: white;
                    border-radius: 12px;
                    width: fit-content;
                    border: 1px solid #e5e7eb;
                }
                
                .talkateave-typing span {
                    width: 8px;
                    height: 8px;
                    border-radius: 50%;
                    background: #9ca3af;
                    animation: typing 1.4s infinite;
                }
                
                .talkateave-typing span:nth-child(2) {
                    animation-delay: 0.2s;
                }
                
                .talkateave-typing span:nth-child(3) {
                    animation-delay: 0.4s;
                }
                
                @keyframes typing {
                    0%, 60%, 100% {
                        transform: translateY(0);
                        opacity: 0.7;
                    }
                    30% {
                        transform: translateY(-10px);
                        opacity: 1;
                    }
                }

                /* Input Container */
                #talkateave-input-container {
                    padding: 12px 16px;
                    background: white;
                    border-top: 1px solid #e5e7eb;
                    display: flex;
                    gap: 8px;
                }
                
                #talkateave-input {
                    flex: 1;
                    padding: 10px 14px;
                    border: 1px solid #e5e7eb;
                    border-radius: 20px;
                    font-size: 14px;
                    outline: none;
                    font-family: inherit;
                }
                
                #talkateave-input:focus {
                    border-color: ${primaryColor};
                }
                
                #talkateave-send-button {
                    width: 40px;
                    height: 40px;
                    border-radius: 50%;
                    background: ${primaryColor};
                    border: none;
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    transition: opacity 0.2s;
                }
                
                #talkateave-send-button:hover:not(:disabled) {
                    opacity: 0.9;
                }
                
                #talkateave-send-button:disabled {
                    opacity: 0.5;
                    cursor: not-allowed;
                }
                
                #talkateave-send-button svg {
                    width: 20px;
                    height: 20px;
                    fill: white;
                }

                /* Powered By */
                .talkateave-powered {
                    text-align: center;
                    padding: 8px;
                    font-size: 11px;
                    color: #9ca3af;
                    border-top: 1px solid #f3f4f6;
                }
                
                .talkateave-powered a {
                    color: ${primaryColor};
                    text-decoration: none;
                }

                /* Mobile Responsive */
                @media (max-width: 480px) {
                    #talkateave-chat-window {
                        width: calc(100vw - 40px);
                        height: calc(100vh - 120px);
                    }
                }
            </style>

            <!-- Chat Button -->
            <button id="talkateave-chat-button" aria-label="Open chat">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                    <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"/>
                </svg>
            </button>

            <!-- Chat Window -->
            <div id="talkateave-chat-window">
                <div id="talkateave-chat-header">
                    <h3>🤖 Chat Assistant</h3>
                    <button id="talkateave-close-button" aria-label="Close chat">
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <line x1="18" y1="6" x2="6" y2="18"></line>
                            <line x1="6" y1="6" x2="18" y2="18"></line>
                        </svg>
                    </button>
                </div>

                <div id="talkateave-messages">
                    <div class="talkateave-message bot">
                        <div class="talkateave-avatar">🤖</div>
                        <div class="talkateave-message-content">
                            Hi! I'm here to help. Ask me anything!
                        </div>
                    </div>
                </div>

                <div id="talkateave-input-container">
                    <input 
                        type="text" 
                        id="talkateave-input" 
                        placeholder="Type your message..."
                        aria-label="Chat message"
                    />
                    <button id="talkateave-send-button" aria-label="Send message">
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
                        </svg>
                    </button>
                </div>

                <div class="talkateave-powered">
                    Powered by <a href="https://yourdomain.com" target="_blank">Talkateave</a>
                </div>
            </div>
        `;

        document.body.appendChild(widgetContainer);
        attachEventListeners();
    }

    // Add message to chat
    function addMessage(content, isUser = false) {
        const messagesContainer = document.getElementById('talkateave-messages');
        const messageDiv = document.createElement('div');
        messageDiv.className = `talkateave-message ${isUser ? 'user' : 'bot'}`;

        messageDiv.innerHTML = `
            <div class="talkateave-avatar">${isUser ? '👤' : '🤖'}</div>
            <div class="talkateave-message-content">${escapeHtml(content)}</div>
        `;

        messagesContainer.appendChild(messageDiv);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;

        // Update chat history
        chatHistory.push({
            role: isUser ? 'user' : 'assistant',
            content: content
        });

        // Keep only last 5 messages
        if (chatHistory.length > MAX_HISTORY * 2) {
            chatHistory = chatHistory.slice(-MAX_HISTORY * 2);
        }
    }

    // Show typing indicator
    function showTypingIndicator() {
        const messagesContainer = document.getElementById('talkateave-messages');
        const typingDiv = document.createElement('div');
        typingDiv.className = 'talkateave-message bot';
        typingDiv.id = 'talkateave-typing-indicator';

        typingDiv.innerHTML = `
            <div class="talkateave-avatar">🤖</div>
            <div class="talkateave-typing">
                <span></span>
                <span></span>
                <span></span>
            </div>
        `;

        messagesContainer.appendChild(typingDiv);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    // Remove typing indicator
    function removeTypingIndicator() {
        const typingIndicator = document.getElementById('talkateave-typing-indicator');
        if (typingIndicator) {
            typingIndicator.remove();
        }
    }

    // Send message to bot
    async function sendMessage(message) {
        if (!message.trim() || isLoading) return;

        isLoading = true;
        const sendButton = document.getElementById('talkateave-send-button');
        const input = document.getElementById('talkateave-input');

        sendButton.disabled = true;
        input.disabled = true;

        // Add user message
        addMessage(message, true);
        input.value = '';

        // Show typing indicator
        showTypingIndicator();

        try {
            const response = await fetch(`${apiUrl}/api/bots/widget/ask?botId=${botId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    message: message,
                    history: chatHistory.slice(-MAX_HISTORY * 2) // Send last 5 exchanges
                })
            });

            if (!response.ok) {
                throw new Error('Failed to get response from bot');
            }

            const answer = await response.text();

            // Remove typing indicator
            removeTypingIndicator();

            // Add bot response
            addMessage(answer, false);

        } catch (error) {
            console.error('TalkAtEve Widget Error:', error);
            removeTypingIndicator();
            addMessage('Sorry, I encountered an error. Please try again.', false);
        } finally {
            isLoading = false;
            sendButton.disabled = false;
            input.disabled = false;
            input.focus();
        }
    }

    // Attach event listeners
    function attachEventListeners() {
        const chatButton = document.getElementById('talkateave-chat-button');
        const closeButton = document.getElementById('talkateave-close-button');
        const chatWindow = document.getElementById('talkateave-chat-window');
        const input = document.getElementById('talkateave-input');
        const sendButton = document.getElementById('talkateave-send-button');

        // Toggle chat window
        chatButton.addEventListener('click', () => {
            isOpen = !isOpen;
            chatWindow.classList.toggle('open');
            if (isOpen) {
                input.focus();
            }
        });

        // Close chat
        closeButton.addEventListener('click', () => {
            isOpen = false;
            chatWindow.classList.remove('open');
        });

        // Send message on button click
        sendButton.addEventListener('click', () => {
            sendMessage(input.value);
        });

        // Send message on Enter key
        input.addEventListener('keypress', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage(input.value);
            }
        });
    }

    // Escape HTML to prevent XSS
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Initialize widget when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', createWidget);
    } else {
        createWidget();
    }

})();