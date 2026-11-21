import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { AuthResponse, DemoSessionResponse, User } from '../models';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = '/api';
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  private remainingQueriesSubject = new BehaviorSubject<number>(5);
  public remainingQueries$ = this.remainingQueriesSubject.asObservable();

  constructor(private http: HttpClient) {
    this.loadStoredUser();
  }

  private loadStoredUser(): void {
    const token = localStorage.getItem('docureader_token');
    const userJson = localStorage.getItem('docureader_user');
    if (token && userJson) {
      try {
        const user = JSON.parse(userJson) as User;
        this.currentUserSubject.next(user);
      } catch (e) {
        this.logout();
      }
    }
  }

  public getToken(): string | null {
    return localStorage.getItem('docureader_token');
  }

  public isAuthenticated(): boolean {
    return !!this.getToken();
  }

  public register(payload: any): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/register`, payload).pipe(
      tap(res => this.setAuth(res))
    );
  }

  public login(payload: any): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/login`, payload).pipe(
      tap(res => this.setAuth(res))
    );
  }

  public startDemo(): Observable<DemoSessionResponse> {
    return this.http.post<DemoSessionResponse>(`${this.apiUrl}/demo/start`, {}).pipe(
      tap(res => {
        const user: User = {
          id: res.userId,
          username: res.username,
          email: `${res.username}@demo.docureader.ai`,
          isDemo: true,
          roles: ['ROLE_DEMO']
        };
        localStorage.setItem('docureader_token', res.token);
        localStorage.setItem('docureader_user', JSON.stringify(user));
        this.remainingQueriesSubject.next(res.remainingQueries);
        this.currentUserSubject.next(user);
      })
    );
  }

  public updateRemainingQueries(remaining: number): void {
    this.remainingQueriesSubject.next(remaining);
  }

  private setAuth(res: AuthResponse): void {
    const user: User = {
      id: res.userId,
      username: res.username,
      email: res.email,
      isDemo: res.isDemo,
      roles: Array.from(res.roles || [])
    };
    localStorage.setItem('docureader_token', res.token);
    localStorage.setItem('docureader_user', JSON.stringify(user));
    this.currentUserSubject.next(user);
    if (!res.isDemo) {
      this.remainingQueriesSubject.next(9999);
    }
  }

  public logout(): void {
    localStorage.removeItem('docureader_token');
    localStorage.removeItem('docureader_user');
    this.currentUserSubject.next(null);
  }
}
