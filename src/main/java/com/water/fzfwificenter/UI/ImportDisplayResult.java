package com.water.fzfwificenter.UI;

import java.util.List;

record ImportDisplayResult(ImportResult importResult, AiDisplayResponse summary, List<String> questions) {
}
