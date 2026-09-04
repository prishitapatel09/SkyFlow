import { useEffect, useRef, useState } from 'react'
import toast from 'react-hot-toast'
import { api, errorMessage } from '@/lib/api'

interface Message {
  role: 'user' | 'assistant'
  text: string
  toolsUsed?: string[]
}

const OPENING_MESSAGE: Message = {
  role: 'assistant',
  text:
    'Hi — I can look up flights, check availability and explain your bookings. ' +
    'What do you need?',
}

/** Turns tool names into something a traveller can read. */
const TOOL_LABELS: Record<string, string> = {
  search_flights: 'searched live availability',
  get_flight: 'checked a flight',
  list_airports: 'looked up airports',
  list_my_bookings: 'read your bookings',
  get_booking: 'read a booking',
  start_booking: 'prepared a checkout link',
}

export default function AssistantWidget() {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<Message[]>([OPENING_MESSAGE])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const conversationId = useRef<string | undefined>(undefined)
  const logRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight, behavior: 'smooth' })
  }, [messages, open])

  async function send(event: React.FormEvent) {
    event.preventDefault()
    const text = draft.trim()
    if (!text || sending) {
      return
    }

    setMessages((current) => [...current, { role: 'user', text }])
    setDraft('')
    setSending(true)

    try {
      const reply = await api.ai.chat(text, conversationId.current)
      conversationId.current = reply.conversationId
      setMessages((current) => [
        ...current,
        { role: 'assistant', text: reply.reply, toolsUsed: reply.toolsUsed },
      ])
    } catch (error) {
      toast.error(errorMessage(error, 'The assistant is unavailable right now.'))
      setMessages((current) => [
        ...current,
        { role: 'assistant', text: 'I could not reach the booking systems just then. Try again?' },
      ])
    } finally {
      setSending(false)
    }
  }

  function reset() {
    if (conversationId.current) {
      void api.ai.clear(conversationId.current)
    }
    conversationId.current = undefined
    setMessages([OPENING_MESSAGE])
  }

  return (
    <>
      {open && (
        <section className="assistant-panel" aria-label="SkyFlow assistant">
          <header className="assistant-head">
            <span>SkyFlow assistant</span>
            <div className="row" style={{ gap: 4 }}>
              <button type="button" onClick={reset} title="Start over" aria-label="Start over">
                ⟳
              </button>
              <button type="button" onClick={() => setOpen(false)} aria-label="Close assistant">
                ×
              </button>
            </div>
          </header>

          <div className="assistant-log" ref={logRef}>
            {messages.map((message, index) => (
              <div
                key={index}
                className={`assistant-msg ${
                  message.role === 'user' ? 'assistant-msg-user' : 'assistant-msg-bot'
                }`}
              >
                {message.text}
                {message.toolsUsed && message.toolsUsed.length > 0 && (
                  <div className="assistant-tools">
                    {message.toolsUsed
                      .map((tool) => TOOL_LABELS[tool] ?? tool)
                      .join(' · ')}
                  </div>
                )}
              </div>
            ))}
            {sending && (
              <div className="assistant-msg assistant-msg-bot muted">
                <span className="spinner spinner-dark" style={{ display: 'inline-block' }} />
              </div>
            )}
          </div>

          <form className="assistant-form" onSubmit={send}>
            <input
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder="Ask about flights or a booking…"
              maxLength={2000}
              aria-label="Message"
            />
            <button type="submit" className="btn btn-sm" disabled={sending || !draft.trim()}>
              Send
            </button>
          </form>
        </section>
      )}

      <button
        type="button"
        className="btn assistant-toggle"
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
      >
        {open ? 'Hide assistant' : 'Ask SkyFlow'}
      </button>
    </>
  )
}
