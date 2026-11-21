import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DocumentDto } from '../models';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class DocumentService {
  private apiUrl = '/api/documents';

  constructor(private http: HttpClient, private authService: AuthService) {}

  private getHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      Authorization: `Bearer ${token}`
    });
  }

  public getDocuments(): Observable<DocumentDto[]> {
    return this.http.get<DocumentDto[]>(this.apiUrl, { headers: this.getHeaders() });
  }

  public uploadDocument(file: File, title?: string): Observable<DocumentDto> {
    const formData = new FormData();
    formData.append('file', file);
    if (title) {
      formData.append('title', title);
    }
    return this.http.post<DocumentDto>(`${this.apiUrl}/upload`, formData, {
      headers: this.getHeaders()
    });
  }

  public deleteDocument(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${id}`, { headers: this.getHeaders() });
  }
}
