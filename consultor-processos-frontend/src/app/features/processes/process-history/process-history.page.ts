import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
  IonInfiniteScroll, IonInfiniteScrollContent, IonRefresher, IonRefresherContent, IonIcon
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { timeOutline, chevronBackOutline, calendarOutline, eyeOutline } from 'ionicons/icons';
import { ProcessService } from '../../../services/process.service';
import { ProcessHistoryEntry } from '../../../models/process.model';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-process-history',
  standalone: true,
  imports: [
    CommonModule,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
    IonInfiniteScroll, IonInfiniteScrollContent, IonRefresher, IonRefresherContent, IonIcon,
    LoadingSpinnerComponent, EmptyStateComponent
  ],
  templateUrl: './process-history.page.html',
  styleUrls: ['./process-history.page.scss']
})
export class ProcessHistoryPage implements OnInit {

  history = signal<ProcessHistoryEntry[]>([]);
  isLoading = signal(true);
  subscriptionId = '';
  hasMore = false;
  private page = 0;

  constructor(
    private route: ActivatedRoute,
    private processService: ProcessService,
    private toast: ToastService
  ) {
    addIcons({ timeOutline, chevronBackOutline, calendarOutline, eyeOutline });
  }

  ngOnInit(): void {
    this.subscriptionId = this.route.snapshot.paramMap.get('id')!;
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.processService.getHistory(this.subscriptionId, this.page).subscribe({
      next: resp => {
        const content = resp.data ?? [];
        this.history.set(content);
        this.hasMore = content.length === 20;
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
        this.toast.error('Erro ao carregar histórico.');
      }
    });
  }

  loadMore(event: any): void {
    this.page++;
    this.processService.getHistory(this.subscriptionId, this.page).subscribe({
      next: resp => {
        const content = resp.data ?? [];
        this.history.update(h => [...h, ...content]);
        this.hasMore = content.length === 20;
        event.target.complete();
      },
      error: () => event.target.complete()
    });
  }

  resetAndLoad(event?: any): void {
    this.page = 0;
    this.history.set([]);
    this.hasMore = false;
    this.processService.getHistory(this.subscriptionId, 0).subscribe({
      next: resp => {
        const content = resp.data ?? [];
        this.history.set(content);
        this.hasMore = content.length === 20;
        event?.target.complete();
      },
      error: () => event?.target.complete()
    });
  }
}