import { createRoot } from 'react-dom/client';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import './styles.css';
import { ApiTest } from './pages/ApiTest';
import { RoleSelect } from './pages/RoleSelect';
import { Login } from './pages/Login';
import { Register } from './pages/Register';
import { ProviderLayout } from './pages/provider/ProviderLayout';
import { AnnotatorLayout } from './pages/annotator/AnnotatorLayout';
import { ProviderDatasetsPage } from './pages/provider/ProviderDatasetsPage';
import { ProviderReviewsPage } from './pages/provider/ProviderReviewsPage';
import { ProviderDisputesPage } from './pages/provider/ProviderDisputesPage';
import { AnnotatorOpenDatasetsPage } from './pages/annotator/AnnotatorOpenDatasetsPage';
import { AnnotatorMyTasksPage } from './pages/annotator/AnnotatorMyTasksPage';
import { AnnotatorReturnedTasksPage } from './pages/annotator/AnnotatorReturnedTasksPage';
import { AnnotatorSubmissionsPage } from './pages/annotator/AnnotatorSubmissionsPage';

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<RoleSelect />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/provider" element={<ProviderLayout />}>
          <Route index element={<Navigate to="datasets" replace />} />
          <Route path="datasets" element={<ProviderDatasetsPage />} />
          <Route path="reviews" element={<ProviderReviewsPage />} />
          <Route path="disputes" element={<ProviderDisputesPage />} />
        </Route>
        <Route path="/annotator" element={<AnnotatorLayout />}>
          <Route index element={<Navigate to="open-datasets" replace />} />
          <Route path="open-datasets" element={<AnnotatorOpenDatasetsPage />} />
          <Route path="my-tasks" element={<AnnotatorMyTasksPage />} />
          <Route path="returned-tasks" element={<AnnotatorReturnedTasksPage />} />
          <Route path="submissions" element={<AnnotatorSubmissionsPage />} />
        </Route>
        <Route path="/dashboard" element={<Navigate to="/" replace />} />
        <Route path="/api-test" element={<ApiTest />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  );
}

const rootNode = document.getElementById('root');

if (!rootNode) {
  throw new Error('Missing #root element');
}

createRoot(rootNode).render(<App />);
