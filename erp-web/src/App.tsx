import { useState } from 'react'
import { NavLink, Route, Routes } from 'react-router'
import { StockPage } from './pages/inventory/StockPage'
import { StockTransactionPage } from './pages/inventory/StockTransactionPage'
import { ItemPage } from './pages/master/ItemPage'
import { PartnerPage } from './pages/master/PartnerPage'
import { WarehousePage } from './pages/master/WarehousePage'
import { GoodsReceiptPage } from './pages/purchase/GoodsReceiptPage'
import { PurchaseOrderListPage } from './pages/purchase/PurchaseOrderListPage'
import { PurchaseOrderPage } from './pages/purchase/PurchaseOrderPage'
import { SalesOrderListPage } from './pages/sales/SalesOrderListPage'
import { SalesOrderPage } from './pages/sales/SalesOrderPage'
import { ShipmentPage } from './pages/sales/ShipmentPage'
import { SystemStatusPage } from './pages/SystemStatusPage'
import { getOperatorId, OPERATORS, setOperatorId } from './session/operator'

type Menu = { label: string; path?: string }

/** 메뉴 구조가 곧 ERP 모듈 구조다. path가 없는 메뉴는 해당 단계에서 열린다 */
const MODULES: { name: string; step: string; menus: Menu[] }[] = [
  {
    name: '기준정보',
    step: '1단계',
    menus: [
      { label: '품목', path: '/master/items' },
      { label: '거래처', path: '/master/partners' },
      { label: '창고', path: '/master/warehouses' },
    ],
  },
  {
    name: '구매',
    step: '2단계',
    menus: [
      { label: '발주', path: '/purchase/orders' },
      { label: '입고', path: '/purchase/receipts' },
    ],
  },
  {
    name: '재고',
    step: '3단계',
    menus: [
      { label: '현재고', path: '/inventory/stocks' },
      { label: '수불 이력', path: '/inventory/transactions' },
    ],
  },
  {
    name: '판매',
    step: '4단계',
    menus: [
      { label: '수주', path: '/sales/orders' },
      { label: '출고', path: '/sales/shipments' },
    ],
  },
]

export default function App() {
  const [operatorId, setOperatorState] = useState(getOperatorId)

  function changeOperator(id: string) {
    setOperatorId(id)
    setOperatorState(id)
  }

  return (
    <div className="layout">
      <header className="topbar">
        <span className="brand">mini-erp</span>
        <span className="brand-sub">구매 · 재고 · 판매</span>
        <label className="operator" title="로그인 대신 쓰는 임시 작업자 선택. 등록자·수정자로 남습니다">
          작업자
          <select value={operatorId} onChange={(e) => changeOperator(e.target.value)}>
            {OPERATORS.map((operator) => (
              <option key={operator.id} value={operator.id}>
                {operator.name} ({operator.id})
              </option>
            ))}
          </select>
        </label>
      </header>

      <nav className="sidebar" aria-label="메뉴">
        <NavLink to="/" end className="menu-item">
          시스템 상태
        </NavLink>
        {MODULES.map((module) => (
          <div key={module.name} className="menu-group">
            <div className="menu-group-title">{module.name}</div>
            {module.menus.map((menu) =>
              menu.path ? (
                <NavLink key={menu.label} to={menu.path} className="menu-item">
                  {menu.label}
                </NavLink>
              ) : (
                <div key={menu.label} className="menu-item disabled" title={`${module.step}에서 구현합니다`}>
                  {menu.label}
                  <span className="badge">{module.step}</span>
                </div>
              ),
            )}
          </div>
        ))}
      </nav>

      <main className="content">
        <Routes>
          <Route path="/" element={<SystemStatusPage />} />
          <Route path="/master/items" element={<ItemPage />} />
          <Route path="/master/partners" element={<PartnerPage />} />
          <Route path="/master/warehouses" element={<WarehousePage />} />
          <Route path="/purchase/orders" element={<PurchaseOrderListPage />} />
          <Route path="/purchase/orders/:id" element={<PurchaseOrderPage />} />
          <Route path="/purchase/receipts" element={<GoodsReceiptPage />} />
          <Route path="/sales/orders" element={<SalesOrderListPage />} />
          <Route path="/sales/orders/:id" element={<SalesOrderPage />} />
          <Route path="/sales/shipments" element={<ShipmentPage />} />
          <Route path="/inventory/stocks" element={<StockPage />} />
          <Route path="/inventory/transactions" element={<StockTransactionPage />} />
          <Route path="*" element={<p className="page-desc">페이지를 찾을 수 없습니다.</p>} />
        </Routes>
      </main>
    </div>
  )
}
