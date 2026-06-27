const BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export type AgentStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE'
export type OrderStatus = 'ASSIGNED' | 'REASSIGNMENT_PENDING' | 'REASSIGNED' | 'DELIVERED'
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED'
export type TriggerReason = 'INITIAL' | 'AGENT_OFFLINE'

export interface Agent {
  id: string
  name: string
  activeOrderCount: number
  status: AgentStatus
}

export interface Order {
  id: string
  description: string
  assignedAgent: Agent | null
  status: OrderStatus
}

export interface Suggestion {
  id: number
  order: Order
  recommendedAgent: Agent
  confidence: number
  reasoning: string
  status: SuggestionStatus
  triggerReason: TriggerReason
}

export interface AssistantMessage {
  role: 'user' | 'assistant'
  text: string
  actionType?: string
}

export const api = {
  chat: (message: string): Promise<{ message: string; actionType: string; data: unknown }> =>
    fetch(`${BASE}/assistant`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message }),
    }).then(r => r.json()),

  getAgents: (): Promise<Agent[]> =>
    fetch(`${BASE}/agents`).then(r => r.json()),

  updateAgentStatus: (id: string, status: AgentStatus): Promise<Agent> =>
    fetch(`${BASE}/agents/${id}/status?status=${status}`, { method: 'PATCH' }).then(r => r.json()),

  getOrders: (status?: OrderStatus): Promise<Order[]> =>
    fetch(`${BASE}/orders${status ? `?status=${status}` : ''}`).then(r => r.json()),

  getSuggestions: (): Promise<Suggestion[]> =>
    fetch(`${BASE}/suggestions`).then(r => r.json()),

  updateSuggestion: (id: number, status: SuggestionStatus): Promise<Suggestion> =>
    fetch(`${BASE}/suggestions/${id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status }),
    }).then(r => r.json()),

  createOrder: (description: string, assignedAgentId?: string): Promise<Order> =>
    fetch(`${BASE}/orders`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ description, assignedAgentId: assignedAgentId || null }),
    }).then(r => r.json()),
}
