<template>
  <main class="chat-page st-page">
    <section class="chat-shell st-panel">
      <header class="chat-header">
        <div>
          <p class="eyebrow">{{ $t('chat.eyebrow') }}</p>
          <h1>{{ $t('chat.titleAlt') }}</h1>
          <p class="subtitle">{{ $t('chat.subtitleDetail') }}</p>
        </div>

        <nav class="header-links">
          <RouterLink class="back-link" to="/devices">
            {{ $t('nav.devices') }}
          </RouterLink>
          <RouterLink class="back-link" to="/knowledge">
            {{ $t('nav.knowledge') }}
          </RouterLink>
          <RouterLink class="back-link" to="/drone">
            {{ $t('chat.backToTasks') }}
          </RouterLink>
        </nav>
      </header>

      <div class="task-bar">
        <label for="task-code">{{ $t('chat.taskCode') }}</label>
        <input
          id="task-code"
          v-model.trim="taskCode"
          maxlength="64"
          :placeholder="$t('chat.taskCodePlaceholder')"
          :disabled="submitting"
        />
        <span class="model-badge">my-drone-expert</span>
        <button
          class="new-chat-button"
          type="button"
          :disabled="submitting"
          @click="startNewConversation"
        >
          {{ $t('chat.newChat') }}
        </button>
      </div>

      <section ref="messagePanel" class="message-panel">
        <div v-if="messages.length === 0" class="empty-state">
          <div class="assistant-mark">AI</div>
          <h2>{{ $t('chat.emptyTitle') }}</h2>
          <p>{{ $t('chat.emptyHint') }}</p>

          <div class="suggestions">
            <button
              v-for="suggestion in suggestions"
              :key="suggestion"
              type="button"
              @click="question = suggestion"
            >
              {{ suggestion }}
            </button>
          </div>
        </div>

        <article
          v-for="message in messages"
          :key="message.id"
          class="message"
          :class="message.role"
        >
          <div class="avatar">
            {{ message.role === 'user' ? $t('chat.userAvatar') : 'AI' }}
          </div>

          <div class="message-content">
            <div class="message-meta">
              <strong>
                {{ message.role === 'user' ? $t('chat.userLabel') : $t('chat.assistantLabel') }}
              </strong>
              <span>{{ message.taskCode }}</span>
              <span v-if="message.sourceCount">
                {{ $t('chat.sourceCount', { count: message.sourceCount }) }}
              </span>
              <span v-if="message.streaming" class="live-status">
                {{ $t('chat.streaming') }}
              </span>
            </div>

            <div class="message-text">
              <span>{{ message.content }}</span>
              <span
                v-if="message.streaming"
                class="stream-cursor"
              ></span>
            </div>

            <div
              v-if="message.workflowId"
              class="workflow-id"
              :title="message.workflowId"
            >
              Temporal: {{ message.workflowId }}
            </div>
          </div>
        </article>

      </section>

      <p v-if="errorMessage" class="error-message">
        {{ errorMessage }}
      </p>

      <form class="composer" @submit.prevent="sendMessage">
        <textarea
          v-model="question"
          rows="3"
          maxlength="2000"
          :placeholder="$t('chat.placeholderDetail')"
          :disabled="submitting"
          @keydown.enter.exact.prevent="sendMessage"
        ></textarea>

        <div class="composer-footer">
          <span>{{ question.length }} / 2000</span>
          <button
            type="submit"
            :disabled="!canSubmit"
          >
            {{ submitting ? $t('chat.generating') : $t('chat.sendAnalyze') }}
          </button>
        </div>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useTranslation } from 'i18next-vue'
import { streamInspectionAnalysis } from '@/api/inspection-task'

type MessageRole = 'user' | 'assistant'

interface ChatMessage {
  id: number
  role: MessageRole
  taskCode: string
  content: string
  workflowId?: string
  streaming?: boolean
  sourceCount?: number
}

const { t } = useTranslation()

const suggestions = computed(() => [
  t('chat.suggestion1'),
  t('chat.suggestion2'),
  t('chat.suggestion3'),
])

const taskCode = ref('TASK-001')
const question = ref('')
const messages = ref<ChatMessage[]>([])
const sessionId = ref(crypto.randomUUID())
const submitting = ref(false)
const errorMessage = ref('')
const messagePanel = ref<HTMLElement>()
let messageId = 0
let scrollFrame: number | undefined

const canSubmit = computed(
  () =>
    !submitting.value &&
    taskCode.value.length > 0 &&
    question.value.trim().length > 0,
)

const scrollToLatest = async () => {
  await nextTick()
  if (messagePanel.value) {
    messagePanel.value.scrollTop = messagePanel.value.scrollHeight
  }
}

const scheduleScrollToLatest = () => {
  if (scrollFrame !== undefined) return
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = undefined
    void scrollToLatest()
  })
}

const sendMessage = async () => {
  const currentQuestion = question.value.trim()
  const currentTaskCode = taskCode.value.trim()

  if (!currentQuestion || !currentTaskCode || submitting.value) {
    return
  }

  errorMessage.value = ''
  messages.value.push({
    id: ++messageId,
    role: 'user',
    taskCode: currentTaskCode,
    content: currentQuestion,
  })
  const assistantId = ++messageId
  messages.value.push({
    id: assistantId,
    role: 'assistant',
    taskCode: currentTaskCode,
    content: '',
    streaming: true,
  })
  question.value = ''
  submitting.value = true
  await scrollToLatest()

  try {
    await streamInspectionAnalysis(
      currentTaskCode,
      sessionId.value,
      currentQuestion,
      {
        onMeta(metadata) {
          const message = messages.value.find(
            item => item.id === assistantId,
          )
          if (message) {
            message.sourceCount = metadata.sources.length
          }
        },
        onToken(content) {
          const message = messages.value.find(
            item => item.id === assistantId,
          )
          if (message) {
            message.content += content
            scheduleScrollToLatest()
          }
        },
      },
    )
    const message = messages.value.find(
      item => item.id === assistantId,
    )
    if (message) {
      message.content = message.content.trim()
    }
  } catch (error) {
    const message = messages.value.find(
      item => item.id === assistantId,
    )
    if (message && !message.content.trim()) {
      message.content = t('chat.interrupted')
    }
    errorMessage.value =
      error instanceof Error ? error.message : t('chat.analyzeFailed')
  } finally {
    const message = messages.value.find(
      item => item.id === assistantId,
    )
    if (message) {
      message.streaming = false
    }
    submitting.value = false
    await scrollToLatest()
  }
}

const startNewConversation = () => {
  sessionId.value = crypto.randomUUID()
  messages.value = []
  question.value = ''
  errorMessage.value = ''
}
</script>

<style scoped>
.chat-page {
  padding: 28px;
}

.chat-shell {
  display: flex;
  flex-direction: column;
  width: min(1080px, 100%);
  height: calc(100vh - 56px);
  min-height: 620px;
  margin: 0 auto;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 28px 18px;
  border-bottom: 1px solid var(--st-border);
}

.chat-header h1 {
  margin: 2px 0 4px;
  font-size: 25px;
}

.back-link {
  padding: 9px 14px;
  color: var(--st-text);
  text-decoration: none;
  background: var(--st-bg-elevated);
  border-radius: 9px;
}

.header-links {
  display: flex;
  gap: 8px;
  padding-right: 220px;
}

.task-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 28px;
  background: var(--st-bg-elevated);
  border-bottom: 1px solid var(--st-border);
}

.task-bar label {
  color: var(--st-text-muted);
  font-size: 13px;
  font-weight: 700;
}

.task-bar input {
  width: 220px;
  padding: 9px 11px;
  color: var(--st-text);
  background: var(--st-input-bg);
  border: 1px solid var(--st-border);
  border-radius: 8px;
  outline: none;
}

.task-bar input:focus,
.composer textarea:focus {
  border-color: var(--st-color-primary);
  box-shadow: 0 0 0 3px var(--st-color-primary-soft);
}

.model-badge {
  margin-left: auto;
  padding: 5px 9px;
  color: var(--st-success);
  font-size: 12px;
  font-weight: 700;
  background: color-mix(in srgb, var(--st-success) 18%, transparent);
  border-radius: 999px;
}

.new-chat-button {
  padding: 7px 11px;
  color: var(--st-text);
  font-weight: 700;
  cursor: pointer;
  background: var(--st-bg-elevated);
  border: 1px solid var(--st-border);
  border-radius: 8px;
}

.new-chat-button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.message-panel {
  flex: 1;
  padding: 26px 28px;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.empty-state {
  display: grid;
  place-items: center;
  max-width: 680px;
  min-height: 100%;
  margin: auto;
  text-align: center;
}

.empty-state h2 {
  margin: 15px 0 6px;
  font-size: 24px;
}

.empty-state p {
  max-width: 520px;
  margin: 0;
  color: var(--st-text-muted);
}

.assistant-mark,
.avatar {
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 800;
  background: linear-gradient(135deg, var(--st-color-primary), var(--st-color-accent));
}

.assistant-mark {
  width: 54px;
  height: 54px;
  border-radius: 17px;
  box-shadow: var(--st-shadow);
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 24px;
}

.suggestions button {
  padding: 9px 13px;
  color: var(--st-text);
  cursor: pointer;
  background: var(--st-bg-elevated);
  border: 1px solid var(--st-border);
  border-radius: 999px;
}

.suggestions button:hover {
  color: var(--st-color-accent);
  border-color: var(--st-border-strong);
}

.message {
  display: flex;
  gap: 12px;
  max-width: 88%;
  margin-bottom: 22px;
}

.message.user {
  flex-direction: row-reverse;
  margin-left: auto;
}

.avatar {
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  font-size: 12px;
}

.user .avatar {
  background: linear-gradient(135deg, var(--st-text-muted), var(--st-text));
}

.message-content {
  min-width: 0;
  padding: 14px 16px;
  background: var(--st-bg-elevated);
  border: 1px solid var(--st-border);
  border-radius: 5px 16px 16px;
}

.user .message-content {
  color: #fff;
  background: var(--st-color-primary);
  border-color: var(--st-color-primary);
  border-radius: 16px 5px 16px 16px;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 8px;
  font-size: 12px;
}

.message-meta span {
  color: var(--st-text-muted);
}

.message-meta .live-status {
  color: var(--st-success);
  font-weight: 700;
}

.user .message-meta span {
  color: color-mix(in srgb, #fff 70%, transparent);
}

.message-text {
  line-height: 1.72;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.stream-cursor {
  display: inline-block;
  width: 7px;
  height: 1.1em;
  margin-left: 3px;
  vertical-align: -0.18em;
  background: var(--st-color-primary);
  border-radius: 2px;
  animation: cursor-blink 0.8s steps(1) infinite;
}

.workflow-id {
  max-width: 600px;
  margin-top: 12px;
  padding-top: 9px;
  overflow: hidden;
  color: var(--st-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-top: 1px solid var(--st-border);
}

.error-message {
  margin: 0 28px 10px;
  padding: 10px 12px;
  color: var(--st-danger);
  font-size: 13px;
  background: color-mix(in srgb, var(--st-danger) 14%, transparent);
  border: 1px solid color-mix(in srgb, var(--st-danger) 35%, transparent);
  border-radius: 9px;
}

.composer {
  margin: 0 28px 24px;
  overflow: hidden;
  background: var(--st-input-bg);
  border: 1px solid var(--st-border);
  border-radius: 15px;
  box-shadow: var(--st-shadow);
}

.composer textarea {
  display: block;
  width: 100%;
  min-height: 74px;
  padding: 14px 15px 8px;
  box-sizing: border-box;
  color: var(--st-text);
  font: inherit;
  resize: none;
  background: transparent;
  border: 0;
  outline: none;
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 9px 9px 15px;
}

.composer-footer span {
  color: var(--st-text-muted);
  font-size: 11px;
}

.composer-footer button {
  padding: 9px 16px;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  background: var(--st-color-primary);
  border: 0;
  border-radius: 9px;
}

.composer-footer button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

@keyframes cursor-blink {
  50% {
    opacity: 0;
  }
}

@media (max-width: 700px) {
  .chat-page {
    padding: 0;
  }

  .chat-shell {
    height: 100vh;
    min-height: 560px;
    border: 0;
    border-radius: 0;
  }

  .chat-header,
  .task-bar,
  .message-panel {
    padding-right: 16px;
    padding-left: 16px;
  }

  .chat-header {
    align-items: flex-start;
  }

  .header-links {
    padding-right: 0;
  }

  .chat-header h1 {
    font-size: 20px;
  }

  .subtitle,
  .model-badge {
    display: none;
  }

  .task-bar input {
    flex: 1;
    width: auto;
  }

  .message {
    max-width: 96%;
  }

  .composer {
    margin-right: 14px;
    margin-bottom: 14px;
    margin-left: 14px;
  }
}
</style>
