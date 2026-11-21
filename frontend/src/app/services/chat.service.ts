import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChatMessage, ChatSession, SourceCitationDto } from '../models';
import { AuthService } from './auth.service';

export interface StreamCallbacks {
  onInit?: (data: { sessionId: number; remainingQueries?: number }) => void;
  onToken: (token: string) => void;
  onCitations: (citations: SourceCitationDto[]) => void;
  onComplete: () => void;
  onError: (error: any) => void;
}

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private apiUrl = '/api/chat';

  constructor(private http: HttpClient, private authService: AuthService) {}

  private getHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      Authorization: `Bearer ${token}`
    });
  }

  public getSessions(): Observable<ChatSession[]> {
    return this.http.get<ChatSession[]>(`${this.apiUrl}/sessions`, { headers: this.getHeaders() });
  }

  public getSession(id: number): Observable<ChatSession> {
    return this.http.get<ChatSession>(`${this.apiUrl}/sessions/${id}`, { headers: this.getHeaders() });
  }

  public async streamChat(
    question: string,
    sessionId: number | null,
    documentIds: number[],
    callbacks: StreamCallbacks
  ): Promise<void> {
    const token = this.authService.getToken();
    const payload = {
      sessionId,
      question,
      documentIds: documentIds.length > 0 ? documentIds : null
    };

    try {
      const response = await fetch(`${this.apiUrl}/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        let errMessage = 'Chat stream failed with status ' + response.status;
        try {
          const errJson = await response.json();
          if (errJson && errJson.message) {
            errMessage = errJson.message;
          }
        } catch (_) {}
        throw new Error(errMessage);
      }

      if (!response.body) {
        throw new Error('ReadableStream not supported by browser');
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';
      let completed = false;

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const events = buffer.split('\n\n');
        buffer = events.pop() || '';

        for (const eventBlock of events) {
          if (!eventBlock.trim()) continue;

          let eventName = 'message';
          let data = '';

          const lines = eventBlock.split('\n');
          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventName = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              const val = line.startsWith('data: ') ? line.substring(6) : line.substring(5);
              data += (data ? '\n' : '') + val;
            }
          }

          if (eventName === 'init') {
            try {
              const initData = JSON.parse(data);
              if (callbacks.onInit) callbacks.onInit(initData);
              if (initData.remainingQueries !== undefined) {
                this.authService.updateRemainingQueries(initData.remainingQueries);
              }
            } catch (_) {}
          } else if (eventName === 'token') {
            try {
              if ((data.startsWith('"') && data.endsWith('"')) || data.startsWith('{') || data.startsWith('[')) {
                callbacks.onToken(JSON.parse(data));
              } else {
                callbacks.onToken(data);
              }
            } catch (_) {
              callbacks.onToken(data);
            }
          } else if (eventName === 'citations') {
            try {
              const citations: SourceCitationDto[] = JSON.parse(data);
              callbacks.onCitations(citations);
            } catch (_) {}
          } else if (eventName === 'done') {
            if (!completed) {
              completed = true;
              callbacks.onComplete();
            }
          }
        }
      }

      if (!completed) {
        completed = true;
        callbacks.onComplete();
      }
    } catch (err) {
      callbacks.onError(err);
    }
  }
}
