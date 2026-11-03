import { SystemStatusPage } from './pages/SystemStatusPage'

/** 메뉴 구조가 곧 ERP 모듈 구조다. 각 모듈은 로드맵 단계에서 하나씩 열린다 */
const MODULES = [
  { name: '기준정보', step: 1, menus: ['품목', '거래처', '창고'] },
  { name: '구매', step: 2, menus: ['발주', '입고'] },
  { name: '재고', step: 3, menus: ['현재고', '수불 이력'] },
  { name: '판매', step: 4, menus: ['수주', '출고'] },
]

export default function App() {
  return (
    <div className="layout">
      <header className="topbar">
        <span className="brand">mini-erp</span>
        <span className="brand-sub">구매 · 재고 · 판매</span>
      </header>

      <nav className="sidebar" aria-label="메뉴">
        <div className="menu-item active" aria-current="page">
          시스템 상태
        </div>
        {MODULES.map((module) => (
          <div key={module.name} className="menu-group">
            <div className="menu-group-title">{module.name}</div>
            {module.menus.map((menu) => (
              <div key={menu} className="menu-item disabled" title={`${module.step}단계에서 구현합니다`}>
                {menu}
                <span className="badge">{module.step}단계</span>
              </div>
            ))}
          </div>
        ))}
      </nav>

      <main className="content">
        <SystemStatusPage />
      </main>
    </div>
  )
}
