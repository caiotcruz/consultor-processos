import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule }       from '@angular/router/testing';
import { IonicModule }               from '@ionic/angular';
import { EmptyStateComponent }       from './empty-state.component';

describe('EmptyStateComponent', () => {
  let component: EmptyStateComponent;
  let fixture:   ComponentFixture<EmptyStateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptyStateComponent, RouterTestingModule, IonicModule.forRoot()]
    }).compileComponents();

    fixture   = TestBed.createComponent(EmptyStateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve criar o componente', () => {
    expect(component).toBeTruthy();
  });

  it('deve renderizar o título passado via @Input', () => {
    component.title = 'Nenhum processo encontrado';
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.empty-title')?.textContent?.trim())
        .toBe('Nenhum processo encontrado');
  });

  it('deve renderizar a mensagem passada via @Input', () => {
    component.message = 'Adicione um processo para começar.';
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.empty-message')?.textContent?.trim())
        .toBe('Adicione um processo para começar.');
  });

  it('deve renderizar o botão de ação quando actionLabel e actionLink forem fornecidos', () => {
    component.actionLabel = 'Adicionar';
    component.actionLink  = '/processes/add';
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('ion-button');
    expect(btn).toBeTruthy();
    expect(btn.textContent.trim()).toContain('Adicionar');
  });

  it('não deve renderizar o botão quando actionLabel estiver vazio', () => {
    component.actionLabel = '';
    component.actionLink  = '/processes/add';
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('ion-button');
    expect(btn).toBeFalsy();
  });

  it('deve usar o ícone padrão quando nenhum for fornecido', () => {
    expect(component.icon).toBe('document-text-outline');
  });
});