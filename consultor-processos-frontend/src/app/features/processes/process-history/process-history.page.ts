import { Component, OnInit, signal } from '@angular/core';
import { CommonModule }      from '@angular/common';
import { ActivatedRoute }    from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
  IonList, IonItem, IonLabel, IonInfiniteScroll, IonInfiniteScrollContent,
  IonRefresher, IonRefresherContent, IonNote
} from '@ionic/angular/standalone';
import { ProcessService }         from '../../../services/process.service';
import { ProcessHistoryEntry }    from '../../../models/process.model';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';
import { EmptyStateComponent }    from '../../shared/components/empty-state/empty-state.component';
import { ToastService }           from '../../../core/services/toast.service';

@Component({
  selector:   'app-process-history',
  standalone: true,
  imports: [
    CommonModule,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
    IonList, IonItem, IonLabel, IonInfiniteScroll, IonInfiniteScrollContent,
    IonRefresher, IonRefresherContent, IonNote,
    LoadingSpinnerComponent, EmptyStateComponent
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-back-button [defaultHref]="'/processes/' + subscriptionId" slot="start"></ion-back-button>
        <ion-title>Histórico</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content>
      <ion-refresher slot="fixed" (ionRefresh)="resetAndLoad($event)">
        <ion-refresher-content></ion-refresher-content>
      </ion-refresher>

      <app-loading-spinner *ngIf="isLoading()"></app-loading-spinner>

      <app-empty-state
        *ngIf="!isLoading() && history().length === 0"
        icon="time-outline"
        title="Sem histórico"
        message="As movimentações detectadas aparecerão aqui após a primeira consulta.">
      </app-empty-state>

      <ion-list *ngIf="!isLoading() && history().length > 0">
        <ion-item *ngFor="let h of history()">
          <ion-label class="ion-text-wrap">
            <ion-note>{{ h.movementDate || (h.detectedAt | date:'dd/MM/yyyy') }}</ion-note>
            <h3>{{ h.description }}</h3>
            <p>Detectado em {{ h.detectedAt | date:'dd/MM/yyyy HH:mm' }}</p>
          </ion-label>
        </ion-item>
      </ion-list>

      <ion-infinite-scroll [disabled]="!hasMore" (ionInfinite)="loadMore($event)">
        <ion-infinite-scroll-content loadingText="Carregando mais...">
        </ion-infinite-scroll-content>
      </ion-infinite-scroll>
    </ion-content>
  `
})
export class ProcessHistoryPage implements OnInit {

  history        = signal<ProcessHistoryEntry[]>([]);
  isLoading      = signal(true);
  subscriptionId = '';
  hasMore        = false;
  private page   = 0;

  constructor(
    private route:          ActivatedRoute,
    private processService: ProcessService,
    private toast:          ToastService
  ) {}

  ngOnInit(): void {
    this.subscriptionId = this.route.snapshot.paramMap.get('id')!;
    this.load();
  }

  load(): void {
    this.processService.getHistory(this.subscriptionId, this.page).subscribe({
      next: resp => {
        const content = resp.data?.content ?? [];
        this.history.update(h => [...h, ...content]);
        this.hasMore  = content.length === 20;
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
        const content = resp.data?.content ?? [];
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
        this.history.set(resp.data?.content ?? []);
        this.hasMore = (resp.data?.content?.length ?? 0) === 20;
        event?.target.complete();
      },
      error: () => event?.target.complete()
    });
  }
}