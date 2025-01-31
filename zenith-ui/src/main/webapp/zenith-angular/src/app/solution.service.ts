import { Injectable } from '@angular/core';
import {HttpClient, HttpHeaders} from "@angular/common/http";
import { Cipher } from "./models/Cipher";
import { SolutionResponse } from "./models/SolutionResponse";
import { SolutionRequest } from "./models/SolutionRequest";

@Injectable({
  providedIn: 'root'
})
export class SolutionService {
  constructor(
    private http: HttpClient
  ) {}

  solve(cipher: Cipher) {
    const headers = new HttpHeaders({
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    });

    let request = new SolutionRequest(cipher.rows, cipher.columns, cipher.ciphertext);

    return this.http.post<SolutionResponse>('http://localhost:8080/api/solutions', request, { headers: headers });
  }
}
