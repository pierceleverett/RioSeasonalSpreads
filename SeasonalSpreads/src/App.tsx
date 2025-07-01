import React, { useState } from "react";
import {
  SignedIn,
  SignedOut,
  UserButton,
  SignInButton,
  RedirectToSignIn,
  useUser,
} from "@clerk/clerk-react";
import SpreadsTab from "./tabs/SpreadsTab.tsx";
import GulfCoastDiffsTab from "./tabs/GulfCoastDiffsTab.tsx";
import ChicagoDiffsTab from "./tabs/ChicagoDiffsTab.tsx";
import TransitTimesTab from "./tabs/TransitTimesTab.tsx";
import MagellanTab from "./tabs/MagellanTab.tsx";
import "./styles/main.css";

type TabOption =
  | "Spreads"
  | "Gulf Coast Diffs"
  | "Chicago Diffs"
  | "Transit Times"
  | "Magellan";

const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabOption>("Spreads");
  const { user } = useUser();

  const tabs: TabOption[] = [
    "Spreads",
    "Gulf Coast Diffs",
    "Chicago Diffs",
    "Transit Times",
    "Magellan",
  ];

  const renderTabContent = () => {
    switch (activeTab) {
      case "Spreads":
        return <SpreadsTab />;
      case "Gulf Coast Diffs":
        return <GulfCoastDiffsTab />;
      case "Chicago Diffs":
        return <ChicagoDiffsTab />;
      case "Transit Times":
        return <TransitTimesTab />;
      case "Magellan":
        return <MagellanTab />;
      default:
        return null;
    }
  };

  return (
    <div className="app-container">
      <header className="app-header">
        <h1 className="app-title">Rio Energy Dashboard</h1>{" "}
        {/* Updated title */}
        <div className="auth-controls">
          <SignedIn>
            <div className="user-greeting">
              <span>Welcome, {user?.firstName || "User"}</span>
              <UserButton afterSignOutUrl="/" />
            </div>
          </SignedIn>
          <SignedOut>
            <SignInButton mode="modal">
              <button className="sign-in-btn">Sign In</button>
            </SignInButton>
          </SignedOut>
        </div>
      </header>

      <main className="app-main">
        <SignedIn>
          <div className="tab-nav-container">
            <nav className="tab-nav">
              {tabs.map((tab) => (
                <button
                  key={tab}
                  onClick={() => setActiveTab(tab)}
                  className={`tab-btn ${activeTab === tab ? "active" : ""}`}
                >
                  {tab}
                </button>
              ))}
            </nav>
          </div>

          <div className="tab-content">{renderTabContent()}</div>
        </SignedIn>

        <SignedOut>
          <div className="sign-in-prompt">
            <RedirectToSignIn />
          </div>
        </SignedOut>
      </main>

      <footer className="app-footer">
        <p>© {new Date().getFullYear()} Rio Energy Dashboard</p>{" "}
        {/* Updated footer */}
      </footer>
    </div>
  );
};

export default App;
