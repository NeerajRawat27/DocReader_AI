import { Component, OnInit, AfterViewInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule, HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { AuthService } from './services/auth.service';
import { DocumentService } from './services/document.service';
import { ChatService } from './services/chat.service';
import { ChatMessage, ChatSession, DocumentDto, SourceCitationDto, User } from './models';

export interface AnswerSegment {
  type: 'text' | 'citation';
  value: string;
  source?: SourceCitationDto | null;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './app.component.html',
  styleUrls: []
})
export class AppComponent implements OnInit, AfterViewInit {
  currentUser: User | null = null;
  remainingQueries = 5;
  totalDemoQueries = 5;

  // View state: 'landing' (Hero + #demo) or 'workspace' (documents & full chat)
  activeTab: 'landing' | 'workspace' = 'landing';
  isLoaded = false;

  // Hero Document Mockup Data (matches 99bugsnofeature.in)
  docLines = [
    { width: 92, highlight: false },
    { width: 78, highlight: false },
    { width: 85, highlight: true },
    { width: 60, highlight: false },
    { width: 90, highlight: false },
    { width: 71, highlight: true },
    { width: 82, highlight: false },
    { width: 55, highlight: false },
    { width: 88, highlight: true },
    { width: 65, highlight: false },
    { width: 79, highlight: false },
    { width: 48, highlight: true }
  ];

  annotations = [
    { label: 'INVOICE TOTAL', value: '$18,450.00', top: 18, delay: 0.9 },
    { label: 'PAYMENT TERMS', value: 'Net 30, 2% early discount', top: 40, delay: 1.3 },
    { label: 'RENEWAL DATE', value: 'Mar 1, 2027 — auto-renews', top: 62, delay: 1.7 },
    { label: 'RISK FLAG', value: 'Uncapped liability clause, p.4', top: 84, delay: 2.1 }
  ];

  // Live Demo Section (#demo) State
  selectedDemoFile: File | null = null;
  usingSample = false;
  isDragging = false;
  demoQuestion = '';
  demoAnswer = '';
  demoSources: SourceCitationDto[] = [];
  isDemoLoading = false;
  isDemoStreaming = false;
  demoStage = '';
  demoError = '';
  demoDocumentId: number | null = null;
  samplePdfUrl = '/assets/sample-document.pdf';

  demoSuggestedQuestions = [
    "What's the total due, and when does it renew?",
    "What are the payment terms and early discount?",
    "Is there an uncapped liability clause?",
    "What are the termination conditions?"
  ];

  // Sidebar navigation in Workspace
  activeSidebarTab: 'documents' | 'history' = 'documents';
  documentSearchFilter = '';

  // Documents
  documents: DocumentDto[] = [];
  selectedDocumentIds: number[] = [];
  isUploading = false;
  uploadError = '';
  customDocTitle = '';

  // Chat
  sessions: ChatSession[] = [];
  currentSessionId: number | null = null;
  messages: ChatMessage[] = [];
  currentQuestion = '';
  isStreaming = false;
  chatError = '';
  copiedMessageIndex: number | null = null;

  // Suggested Prompts
  promptSuggestions = [
    'What are the core architecture components of this platform?',
    'How does the sliding-window text chunking and overlap function?',
    'Explain the security model, JWT authentication, and document isolation.',
    'What rate limits are enforced for guest demo sessions?'
  ];

  // Auth modal
  showAuthModal = false;
  authMode: 'login' | 'register' = 'login';
  authUsername = '';
  authEmail = '';
  authPassword = '';
  authError = '';
  authLoading = false;

  constructor(
    public authService: AuthService,
    private documentService: DocumentService,
    private chatService: ChatService,
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
      if (user) {
        this.loadDocuments();
        this.loadSessions();
      } else {
        this.documents = [];
        this.sessions = [];
        this.messages = [];
        this.currentSessionId = null;
      }
      this.cdr.detectChanges();
    });

    this.authService.remainingQueries$.subscribe(rem => {
      this.remainingQueries = rem;
      this.cdr.detectChanges();
    });

    if (!this.authService.isAuthenticated()) {
      this.startDemoMode();
    }
  }

  ngAfterViewInit(): void {
    setTimeout(() => {
      this.isLoaded = true;
      this.cdr.detectChanges();
    }, 100);
  }

  scrollToDemo(): void {
    if (this.activeTab !== 'landing') {
      this.activeTab = 'landing';
    }
    setTimeout(() => {
      const el = document.getElementById('demo');
      if (el) {
        el.scrollIntoView({ behavior: 'smooth' });
      }
    }, 50);
  }

  startDemoMode(): void {
    this.authService.startDemo().subscribe({
      next: res => {
        this.showAuthModal = false;
        this.totalDemoQueries = res.totalQueriesAllowed || 5;
        this.loadDocuments();
      },
      error: err => {
        console.error('Failed to initiate guest demo session', err);
      }
    });
  }

  loadDocuments(): void {
    if (!this.authService.isAuthenticated()) return;
    this.documentService.getDocuments().subscribe({
      next: docs => {
        this.documents = docs;
        this.cdr.detectChanges();
      },
      error: err => console.error('Failed to load documents', err)
    });
  }

  loadSessions(): void {
    if (!this.authService.isAuthenticated()) return;
    this.chatService.getSessions().subscribe({
      next: sess => {
        this.sessions = sess;
        this.cdr.detectChanges();
      },
      error: err => console.error('Failed to load chat sessions', err)
    });
  }

  onFileSelected(event: any): void {
    const file: File = event.target.files[0];
    if (!file) return;

    this.isUploading = true;
    this.uploadError = '';

    const doUpload = () => {
      this.documentService.uploadDocument(file, this.customDocTitle).subscribe({
        next: doc => {
          this.documents.unshift(doc);
          this.isUploading = false;
          this.customDocTitle = '';
          event.target.value = '';
          this.cdr.detectChanges();
        },
        error: err => {
          if (err.status === 401 || err.status === 403) {
            this.authService.startDemo().subscribe({
              next: () => doUpload(),
              error: () => {
                this.uploadError = 'Session expired. Please log in or refresh.';
                this.isUploading = false;
                this.cdr.detectChanges();
              }
            });
            return;
          }
          this.uploadError = err.error?.message || 'Failed to upload document. Max 25MB supported.';
          this.isUploading = false;
          this.cdr.detectChanges();
        }
      });
    };
    doUpload();
  }

  deleteDocument(id: number, event: MouseEvent): void {
    event.stopPropagation();
    if (!confirm('Are you sure you want to delete this document and its vector embeddings?')) return;

    this.documentService.deleteDocument(id).subscribe({
      next: () => {
        this.documents = this.documents.filter(d => d.id !== id);
        this.selectedDocumentIds = this.selectedDocumentIds.filter(docId => docId !== id);
        this.cdr.detectChanges();
      },
      error: err => console.error('Error deleting document', err)
    });
  }

  toggleDocumentSelection(id: number): void {
    const idx = this.selectedDocumentIds.indexOf(id);
    if (idx > -1) {
      this.selectedDocumentIds.splice(idx, 1);
    } else {
      this.selectedDocumentIds.push(id);
    }
  }

  isDocSelected(id: number): boolean {
    return this.selectedDocumentIds.includes(id);
  }

  selectAllDocs(): void {
    if (this.selectedDocumentIds.length === this.documents.length) {
      this.selectedDocumentIds = [];
    } else {
      this.selectedDocumentIds = this.documents.map(d => d.id);
    }
  }

  get filteredDocuments(): DocumentDto[] {
    if (!this.documentSearchFilter.trim()) return this.documents;
    const q = this.documentSearchFilter.toLowerCase();
    return this.documents.filter(d => d.title.toLowerCase().includes(q) || d.originalFilename.toLowerCase().includes(q));
  }

  selectSession(session: ChatSession): void {
    this.currentSessionId = session.id;
    this.chatService.getSession(session.id).subscribe({
      next: detail => {
        this.messages = detail.messages || [];
        this.cdr.detectChanges();
      },
      error: err => console.error('Failed to load session details', err)
    });
  }

  newChat(): void {
    this.currentSessionId = null;
    this.messages = [];
    this.chatError = '';
    this.cdr.detectChanges();
  }

  useSuggestion(prompt: string): void {
    this.currentQuestion = prompt;
    this.sendQuestion();
  }

  sendQuestion(): void {
    if (!this.currentQuestion.trim() || this.isStreaming) return;

    if (this.documents.length === 0) {
      this.chatError = 'Please upload a PDF or DOCX document first on the left to begin querying.';
      return;
    }

    const q = this.currentQuestion.trim();
    this.currentQuestion = '';
    this.chatError = '';

    this.messages.push({
      role: 'USER',
      content: q,
      createdAt: new Date().toISOString()
    });

    const assistantMsg: ChatMessage = {
      role: 'ASSISTANT',
      content: '',
      isStreaming: true,
      citations: [],
      createdAt: new Date().toISOString()
    };
    this.messages.push(assistantMsg);
    this.isStreaming = true;
    this.cdr.detectChanges();

    this.chatService.streamChat(
      q,
      this.currentSessionId,
      this.selectedDocumentIds,
      {
        onInit: data => {
          this.currentSessionId = data.sessionId;
          this.loadSessions();
        },
        onToken: token => {
          assistantMsg.content += token;
          this.cdr.detectChanges();
        },
        onCitations: citations => {
          assistantMsg.citations = citations;
          this.cdr.detectChanges();
        },
        onComplete: () => {
          assistantMsg.isStreaming = false;
          this.isStreaming = false;
          this.cdr.detectChanges();
        },
        onError: err => {
          assistantMsg.isStreaming = false;
          this.isStreaming = false;
          this.chatError = err.message || 'Stream connection interrupted.';
          this.cdr.detectChanges();
        }
      }
    );
  }

  copyToClipboard(text: string, index: number): void {
    navigator.clipboard.writeText(text).then(() => {
      this.copiedMessageIndex = index;
      setTimeout(() => {
        this.copiedMessageIndex = null;
        this.cdr.detectChanges();
      }, 2000);
      this.cdr.detectChanges();
    });
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = true;
  }

  onDragLeave(): void {
    this.isDragging = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = false;
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.setDemoFile(files[0]);
    }
  }

  onDemoFileSelected(event: any): void {
    const file: File = event.target.files?.[0];
    if (file) {
      this.setDemoFile(file);
    }
  }

  setDemoFile(file: File): void {
    const lower = file.name.toLowerCase();
    if (!lower.endsWith('.pdf') && !lower.endsWith('.docx') && !lower.endsWith('.txt')) {
      this.demoError = 'Please upload a PDF or DOCX file (max 5MB).';
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.demoError = 'File size exceeds 5MB limit for demo mode.';
      return;
    }
    this.demoError = '';
    this.usingSample = false;
    this.selectedDemoFile = file;
    this.demoDocumentId = null;
    this.demoAnswer = '';
    this.demoSources = [];
  }

  useSampleDocument(): void {
    this.demoError = '';
    this.selectedDemoFile = null;
    this.usingSample = true;
    this.demoDocumentId = null;
    this.demoAnswer = '';
    this.demoSources = [];
  }

  clearDemoFile(): void {
    this.selectedDemoFile = null;
    this.usingSample = false;
    this.demoDocumentId = null;
    this.demoAnswer = '';
    this.demoSources = [];
    this.demoError = '';
  }

  useDemoPrompt(question: string): void {
    this.demoQuestion = question;
    if (!this.selectedDemoFile && !this.usingSample) {
      this.usingSample = true;
    }
  }

  askDemoQuestion(): void {
    if (!this.demoQuestion.trim()) {
      this.demoError = 'Please enter a question.';
      return;
    }
    // Auto-fallback to sample contract if user hasn't selected another file
    if (!this.selectedDemoFile && !this.usingSample) {
      this.usingSample = true;
    }
    if (this.isDemoLoading || this.isDemoStreaming) {
      return;
    }

    this.demoError = '';
    this.demoAnswer = '';
    this.demoSources = [];
    this.isDemoLoading = true;
    this.demoStage = 'Reading & verifying document...';
    this.cdr.detectChanges();

    const startInference = () => {
      const proceedWithChat = (docId: number) => {
        this.demoStage = 'Searching relevant clauses & generating answer...';
        this.isDemoStreaming = true;
        this.cdr.detectChanges();

        this.chatService.streamChat(
          this.demoQuestion,
          null,
          [docId],
          {
            onInit: () => {
              this.isDemoLoading = false;
            },
            onToken: (token: string) => {
              this.isDemoLoading = false;
              this.demoAnswer += token;
              this.cdr.detectChanges();
            },
            onCitations: (citations: SourceCitationDto[]) => {
              this.demoSources = citations;
              this.cdr.detectChanges();
            },
            onComplete: () => {
              this.isDemoLoading = false;
              this.isDemoStreaming = false;
              this.demoStage = '';
              this.cdr.detectChanges();
            },
            onError: (err: any) => {
              this.isDemoLoading = false;
              this.isDemoStreaming = false;
              this.demoStage = '';
              this.demoError = err.message || 'Stream connection interrupted. Try again.';
              this.cdr.detectChanges();
            }
          }
        );
      };

      if (this.demoDocumentId) {
        proceedWithChat(this.demoDocumentId);
      } else if (this.usingSample) {
        this.demoStage = 'Loading sample agreement...';
        this.http.get(this.samplePdfUrl, { responseType: 'blob' }).subscribe({
          next: (blob: Blob) => {
            const sampleFile = new File([blob], 'sample-document.pdf', { type: 'application/pdf' });
            const uploadSample = () => {
              this.documentService.uploadDocument(sampleFile, 'Sample Software Services Agreement').subscribe({
                next: doc => {
                  this.demoDocumentId = doc.id;
                  this.documents.unshift(doc);
                  proceedWithChat(doc.id);
                },
                error: err => {
                  if (err.status === 401 || err.status === 403) {
                    this.authService.startDemo().subscribe({
                      next: () => uploadSample(),
                      error: () => {
                        this.isDemoLoading = false;
                        this.demoError = 'Session expired. Please refresh the page.';
                        this.cdr.detectChanges();
                      }
                    });
                    return;
                  }
                  this.isDemoLoading = false;
                  this.demoError = err.error?.message || 'Failed to index sample document. Please try again.';
                  this.cdr.detectChanges();
                }
              });
            };
            uploadSample();
          },
          error: () => {
            this.isDemoLoading = false;
            this.demoError = 'Failed to load sample document.';
            this.cdr.detectChanges();
          }
        });
      } else if (this.selectedDemoFile) {
        this.demoStage = 'Extracting text & generating vector embeddings...';
        const fileToUpload = this.selectedDemoFile;
        const uploadUserFile = () => {
          this.documentService.uploadDocument(fileToUpload, fileToUpload.name).subscribe({
            next: doc => {
              this.demoDocumentId = doc.id;
              this.documents.unshift(doc);
              proceedWithChat(doc.id);
            },
            error: err => {
              if (err.status === 401 || err.status === 403) {
                this.authService.startDemo().subscribe({
                  next: () => uploadUserFile(),
                  error: () => {
                    this.isDemoLoading = false;
                    this.demoError = 'Session expired. Please refresh the page.';
                    this.cdr.detectChanges();
                  }
                });
                return;
              }
              this.isDemoLoading = false;
              this.demoError = err.error?.message || 'Failed to upload and index document.';
              this.cdr.detectChanges();
            }
          });
        };
        uploadUserFile();
      }
    };

    if (!this.authService.isAuthenticated()) {
      this.authService.startDemo().subscribe({
        next: () => startInference(),
        error: () => {
          this.isDemoLoading = false;
          this.demoError = 'Could not establish guest session. Please refresh.';
          this.cdr.detectChanges();
        }
      });
    } else {
      startInference();
    }
  }

  renderFormattedAnswer(content: string, citations: SourceCitationDto[] = []): SafeHtml {
    if (!content) return '';

    // Step 1: Escape raw HTML entities
    let text = content
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    // Step 2: Normalize inline bullets (e.g., "provide: * A * B") onto separate lines
    text = text.replace(/([^\n])\s+[\*\-\•]\s+/g, '$1\n* ');

    // Step 3: Split into lines and structure into paragraphs and lists
    const rawLines = text.split('\n');
    const output: string[] = [];
    let inUl = false;
    let inOl = false;

    const closeLists = () => {
      if (inUl) {
        output.push('</ul>');
        inUl = false;
      }
      if (inOl) {
        output.push('</ol>');
        inOl = false;
      }
    };

    for (let i = 0; i < rawLines.length; i++) {
      const line = rawLines[i].trim();

      if (!line) {
        closeLists();
        continue;
      }

      // Heading 3 or 4: ### or ##
      const h3Match = line.match(/^###\s+(.+)$/);
      const h2Match = line.match(/^##\s+(.+)$/);
      if (h3Match) {
        closeLists();
        output.push(`<h4 class="answer__h4">${this.formatInline(h3Match[1], citations)}</h4>`);
        continue;
      }
      if (h2Match) {
        closeLists();
        output.push(`<h3 class="answer__h3">${this.formatInline(h2Match[1], citations)}</h3>`);
        continue;
      }

      // Bullet lists: starts with * or - or •
      const ulMatch = line.match(/^[\*\-\•]\s+(.+)$/);
      if (ulMatch) {
        if (inOl) {
          output.push('</ol>');
          inOl = false;
        }
        if (!inUl) {
          output.push('<ul class="answer__list">');
          inUl = true;
        }
        output.push(`<li>${this.formatInline(ulMatch[1], citations)}</li>`);
        continue;
      }

      // Numbered lists: 1. or 1)
      const olMatch = line.match(/^\d+[\.\)]\s+(.+)$/);
      if (olMatch) {
        if (inUl) {
          output.push('</ul>');
          inUl = false;
        }
        if (!inOl) {
          output.push('<ol class="answer__olist">');
          inOl = true;
        }
        output.push(`<li>${this.formatInline(olMatch[1], citations)}</li>`);
        continue;
      }

      // Normal paragraph
      closeLists();
      output.push(`<p class="answer__p">${this.formatInline(line, citations)}</p>`);
    }

    closeLists();

    const finalHtml = output.join('');
    return this.sanitizer.bypassSecurityTrustHtml(finalHtml);
  }

  private formatInline(str: string, citations: SourceCitationDto[] = []): string {
    // 1. Bold: **text** or __text__
    let res = str
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/__([^_]+)__/g, '<strong>$1</strong>')
      // 2. Inline code: `code`
      .replace(/`([^`]+)`/g, '<code class="answer__code">$1</code>');

    // 3. Citations: [1], [Source 1], [sample.pdf, p.2]
    res = res.replace(/\[(?:Source\s*)?(\d+)\]/gi, (match, p1) => {
      const idx = parseInt(p1, 10);
      const src = citations && citations[idx - 1] ? citations[idx - 1] : null;
      const safeDocTitle = (src?.documentTitle || '').replace(/"/g, '&quot;');
      const title = src
        ? `Source ${idx}: ${safeDocTitle} (Page ${src.pageNumber})`
        : `Source ${idx}`;
      return `<sup class="demo__citation" title="${title}">[${idx}]</sup>`;
    });

    res = res.replace(/\[([^\]]+?),\s*p(?:age)?\.\s*(\d+)\]/gi, (match, docName, pageNum) => {
      const p = parseInt(pageNum, 10);
      const matchedIdx = citations.findIndex(c => c.pageNumber === p || (c.documentTitle && c.documentTitle.toLowerCase().includes(docName.toLowerCase().trim())));
      const displayNum = matchedIdx >= 0 ? (matchedIdx + 1) : pageNum;
      const safeDocName = (docName || '').trim().replace(/"/g, '&quot;');
      const title = `${safeDocName} (Page ${pageNum})`;
      return `<sup class="demo__citation" title="${title}">[${displayNum}]</sup>`;
    });

    return res;
  }

  get parsedDemoSegments(): AnswerSegment[] {
    return this.parseAnswerSegments(this.demoAnswer, this.demoSources);
  }

  parseAnswerSegments(text: string, sources: SourceCitationDto[] = []): AnswerSegment[] {
    if (!text) return [];
    const regex = /\[(\d+)\]/g;
    const segments: AnswerSegment[] = [];
    let lastIndex = 0;
    let match: RegExpExecArray | null;

    while ((match = regex.exec(text)) !== null) {
      if (match.index > lastIndex) {
        segments.push({ type: 'text', value: text.substring(lastIndex, match.index) });
      }
      const num = match[1];
      const sourceIdx = parseInt(num, 10) - 1;
      const src = sources && sources[sourceIdx] ? sources[sourceIdx] : null;
      segments.push({ type: 'citation', value: num, source: src });
      lastIndex = match.index + match[0].length;
    }
    if (lastIndex < text.length) {
      segments.push({ type: 'text', value: text.substring(lastIndex) });
    }
    return segments;
  }

  handleAuthSubmit(): void {
    this.authError = '';
    this.authLoading = true;

    if (this.authMode === 'login') {
      this.authService.login({
        username: this.authUsername,
        password: this.authPassword
      }).subscribe({
        next: () => {
          this.showAuthModal = false;
          this.authLoading = false;
          this.authPassword = '';
        },
        error: err => {
          this.authError = err.error?.message || 'Invalid username or password.';
          this.authLoading = false;
        }
      });
    } else {
      this.authService.register({
        username: this.authUsername,
        email: this.authEmail,
        password: this.authPassword
      }).subscribe({
        next: () => {
          this.showAuthModal = false;
          this.authLoading = false;
          this.authPassword = '';
        },
        error: err => {
          this.authError = err.error?.message || 'Registration failed. Check details.';
          this.authLoading = false;
        }
      });
    }
  }

  openAuthModal(mode: 'login' | 'register'): void {
    this.authMode = mode;
    this.authError = '';
    this.showAuthModal = true;
  }

  getFileType(filename: string): 'PDF' | 'DOCX' | 'TXT' {
    if (!filename) return 'TXT';
    const lower = filename.toLowerCase();
    if (lower.endsWith('.pdf')) return 'PDF';
    if (lower.endsWith('.docx')) return 'DOCX';
    return 'TXT';
  }

  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }
}
