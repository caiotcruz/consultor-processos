// src/app/features/processes/process-list/process-list.page.ts
import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonButtons, IonButton,
  IonIcon, IonFab, IonFabButton, IonList, IonItem, IonLabel,
  IonBadge, IonRefresher, IonRefresherContent, IonSegment,
  IonSegmentButton, IonSkeletonText, IonNote
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { add, personCircleOutline, refreshOutline, documentTextOutline, chevronForwardOutline } from 'ionicons/icons';
import { ProcessService } from '../../../services/process.service';
import { ProcessSummary } from '../../../models/process.model';
import { ToastService } from '../../../core/services/toast.service';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-process-list',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle, IonButtons, IonButton,
    IonIcon, IonFab, IonLabel,
    IonRefresher, IonRefresherContent, IonSegment,
    IonSegmentButton, IonSkeletonText, FormsModule
  ],
  templateUrl: './process-list.page.html',
  styleUrls: ['./process-list.page.scss']
})
export class ProcessListPage implements OnInit {

  processes = signal<ProcessSummary[]>([]);
  isLoading = signal(true);
  activeFilter = 'all';

  constructor(
    private processService: ProcessService,
    private toast: ToastService
  ) {
    addIcons({ add, personCircleOutline, refreshOutline, documentTextOutline, chevronForwardOutline });
  }

  ngOnInit(): void { 
    this.load(); 
  }

  load(event?: any): void {
    if (!event) {
      this.isLoading.set(true);
    }
    const params = this.activeFilter === 'all' ? {} : { active: this.activeFilter === 'true' };

    this.processService.list(params)
      .pipe(
        finalize(() => {
          if (event?.target) {
            event.target.complete();
          }
          this.isLoading.set(false);
        })
      )
      .subscribe({
        next: resp => {
          const data = resp.data as any;
          this.processes.set(Array.isArray(data) ? data : []);
        },
        error: () => {
          this.toast.error('Erro ao carregar processos.');
        }
      });
  }

  onFilterChange(): void { 
    this.load(); 
  }

  statusLabel(status: string): string {
    return { PENDING: 'Pendente', OK: 'Ativo', ERROR: 'Erro', BLOCKED: 'Bloqueado' }[status] ?? status;
  }
}