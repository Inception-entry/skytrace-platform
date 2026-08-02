<template>
  <main class="chat-page">
    <section class="chat-shell">
      <header class="chat-header">
        <div>
          <p class="eyebrow">SKYTRACE AI COPILOT</p>
          <h1>无人机巡检 AI 分析</h1>
          <p class="subtitle">
            LangChain · Ollama · RAG 实时流式分析
          </p>
        </div>

        <nav class="header-links">
          <RouterLink class="back-link" to="/devices">
            设备管理
          </RouterLink>
          <RouterLink class="back-link" to="/knowledge">
            知识库
          </RouterLink>
          <RouterLink class="back-link" to="/drone">
            返回任务列表
          </RouterLink>
        </nav>
      </header>

      <div class="task-bar">
        <label for="task-code">当前任务编号</label>
        <input
          id="task-code"
          v-model.trim="taskCode"
          maxlength="64"
          placeholder="例如 TASK-001"
          :disabled="submitting"
        />
        <span class="model-badge">my-drone-expert</span>
        <button
          class="new-chat-button"
          type="button"
          :disabled="submitting"
          @click="startNewConversation"
        >
          新对话
        </button>
      </div>

      <section ref="messagePanel" class="message-panel">
        <div v-if="messages.length === 0" class="empty-state">
          <div class="assistant-mark">AI</div>
          <h2>需要分析什么？</h2>
          <p>
            输入巡检任务编号，然后询问飞行安全、通信、
            告警处置或故障恢复问题。
          </p>

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
            {{ message.role === 'user' ? '我' : 'AI' }}
          </div>

          <div class="message-content">
            <div class="message-meta">
              <strong>
                {{ message.role === 'user' ? '巡检人员' : '无人机助手' }}
              </strong>
              <span>{{ message.taskCode }}</span>
              <span v-if="message.sourceCount">
                {{ message.sourceCount }} 条知识来源
              </span>
              <span v-if="message.streaming" class="live-status">
                实时生成中
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
          placeholder="输入问题，Enter 发送，Shift + Enter 换行"
          :disabled="submitting"
          @keydown.enter.exact.prevent="sendMessage"
        ></textarea>

        <div class="composer-footer">
          <span>{{ question.length }} / 2000</span>
          <button
            type="submit"
            :disabled="!canSubmit"
          >
            {{ submitting ? '生成中' : '发送分析' }}
          </button>
        </div>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
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

const suggestions = [
  '分析当前任务可能存在的通信风险',
  '无人机图传中断后应该如何处置？',
  '给出低电量返航的安全检查清单',
]

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
      message.content = '回答生成中断，请稍后重试。'
    }
    errorMessage.value =
      error instanceof Error ? error.message : 'AI 分析失败，请稍后重试'
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
  min-height: 100vh;
  padding: 28px;
  box-sizing: border-box;
  color: #172033;
  background:
    radial-gradient(circle at 12% 8%, #dbeafe 0, transparent 32%),
    radial-gradient(circle at 88% 92%, #dcfce7 0, transparent 28%),
    #f5f7fb;
}

.chat-shell {
  display: flex;
  flex-direction: column;
  width: min(1080px, 100%);
  height: calc(100vh - 56px);
  min-height: 620px;
  margin: 0 auto;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 22px;
  box-shadow: 0 24px 70px rgba(30, 64, 175, 0.12);
  backdrop-filter: blur(14px);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 28px 18px;
  border-bottom: 1px solid #e8edf5;
}

.chat-header h1 {
  margin: 2px 0 4px;
  font-size: 25px;
}

.eyebrow {
  margin: 0;
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.subtitle {
  margin: 0;
  color: #748198;
  font-size: 13px;
}

.back-link {
  padding: 9px 14px;
  color: #334155;
  text-decoration: none;
  background: #f1f5f9;
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
  background: #fbfcff;
  border-bottom: 1px solid #e8edf5;
}

.task-bar label {
  color: #526077;
  font-size: 13px;
  font-weight: 700;
}

.task-bar input {
  width: 220px;
  padding: 9px 11px;
  color: #172033;
  background: white;
  border: 1px solid #d8e0ec;
  border-radius: 8px;
  outline: none;
}

.task-bar input:focus,
.composer textarea:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}

.model-badge {
  margin-left: auto;
  padding: 5px 9px;
  color: #047857;
  font-size: 12px;
  font-weight: 700;
  background: #d1fae5;
  border-radius: 999px;
}

.new-chat-button {
  padding: 7px 11px;
  color: #334155;
  font-weight: 700;
  cursor: pointer;
  background: white;
  border: 1px solid #d8e0ec;
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
  color: #6b7890;
}

.assistant-mark,
.avatar {
  display: grid;
  place-items: center;
  color: white;
  font-weight: 800;
  background: linear-gradient(135deg, #2563eb, #0ea5e9);
}

.assistant-mark {
  width: 54px;
  height: 54px;
  border-radius: 17px;
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.25);
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
  color: #334155;
  cursor: pointer;
  background: white;
  border: 1px solid #dbe3ef;
  border-radius: 999px;
}

.suggestions button:hover {
  color: #1d4ed8;
  border-color: #93c5fd;
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
  background: linear-gradient(135deg, #334155, #64748b);
}

.message-content {
  min-width: 0;
  padding: 14px 16px;
  background: #f4f7fb;
  border: 1px solid #e4eaf2;
  border-radius: 5px 16px 16px;
}

.user .message-content {
  color: white;
  background: #2563eb;
  border-color: #2563eb;
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
  color: #8490a3;
}

.message-meta .live-status {
  color: #047857;
  font-weight: 700;
}

.user .message-meta span {
  color: #bfdbfe;
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
  background: #2563eb;
  border-radius: 2px;
  animation: cursor-blink 0.8s steps(1) infinite;
}

.workflow-id {
  max-width: 600px;
  margin-top: 12px;
  padding-top: 9px;
  overflow: hidden;
  color: #77849a;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-top: 1px solid #dde5ef;
}

.loading-card {
  display: flex;
  align-items: center;
  gap: 5px;
}

.loading-card span {
  width: 7px;
  height: 7px;
  background: #3b82f6;
  border-radius: 50%;
  animation: pulse 1s infinite alternate;
}

.loading-card span:nth-child(2) {
  animation-delay: 0.2s;
}

.loading-card span:nth-child(3) {
  animation-delay: 0.4s;
}

.loading-card p {
  margin: 0 0 0 7px;
  color: #64748b;
  font-size: 13px;
}

.error-message {
  margin: 0 28px 10px;
  padding: 10px 12px;
  color: #b91c1c;
  font-size: 13px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 9px;
}

.composer {
  margin: 0 28px 24px;
  overflow: hidden;
  background: white;
  border: 1px solid #dbe3ef;
  border-radius: 15px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.07);
}

.composer textarea {
  display: block;
  width: 100%;
  min-height: 74px;
  padding: 14px 15px 8px;
  box-sizing: border-box;
  color: #172033;
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
  color: #94a3b8;
  font-size: 11px;
}

.composer-footer button {
  padding: 9px 16px;
  color: white;
  font-weight: 700;
  cursor: pointer;
  background: #2563eb;
  border: 0;
  border-radius: 9px;
}

.composer-footer button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

@keyframes pulse {
  from {
    opacity: 0.35;
    transform: translateY(2px);
  }
  to {
    opacity: 1;
    transform: translateY(-2px);
  }
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
