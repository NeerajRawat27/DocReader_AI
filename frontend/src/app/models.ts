export interface User {
  id: number;
  username: string;
  email: string;
  isDemo: boolean;
  roles: string[];
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  userId: number;
  username: string;
  email: string;
  isDemo: boolean;
  roles: string[];
}

export interface DemoSessionResponse {
  token: string;
  userId: number;
  username: string;
  remainingQueries: number;
  totalQueriesAllowed: number;
  expiresAtTimestamp: number;
}

export interface DocumentDto {
  id: number;
  title: string;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  pageCount: number;
  chunkCount: number;
  createdAt: string;
}

export interface SourceCitationDto {
  documentId: number;
  documentTitle: string;
  pageNumber: number;
  chunkIndex: number;
  excerpt: string;
  relevanceScore: number;
  citationIndex?: number;
  chunkText?: string;
}

export interface ChatMessage {
  id?: number;
  role: 'USER' | 'ASSISTANT';
  content: string;
  citations?: SourceCitationDto[];
  createdAt?: string;
  isStreaming?: boolean;
}

export interface ChatSession {
  id: number;
  title: string;
  isDemo: boolean;
  createdAt: string;
  messages?: ChatMessage[];
}
