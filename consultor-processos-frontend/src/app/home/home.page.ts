import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector:   'app-home',
  standalone: true,
  imports:    [],
  template:   ''   // sem UI — redirect imediato
})
export class HomePage implements OnInit {
  constructor(private router: Router) {}
  ngOnInit(): void { this.router.navigate(['/processes'], { replaceUrl: true }); }
}