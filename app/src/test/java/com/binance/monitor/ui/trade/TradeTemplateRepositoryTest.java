package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.model.v2.trade.TradeTemplate;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TradeTemplateRepositoryTest {

    @Test
    public void shouldFallbackToSystemTemplatesWhenStoreIsEmpty() {
        FakeStore store = new FakeStore();
        TradeTemplateRepository repository = new TradeTemplateRepository(store);

        List<TradeTemplate> templates = repository.getTemplates();
        TradeTemplate defaultTemplate = repository.getDefaultTemplate();

        assertFalse(templates.isEmpty());
        assertEquals("default_market", defaultTemplate.getTemplateId());
        assertEquals(0.05d, defaultTemplate.getDefaultVolume(), 0.0000001d);
    }

    @Test
    public void saveTemplatesShouldRoundTripAndUpdateDefaultTemplate() {
        FakeStore store = new FakeStore();
        TradeTemplateRepository repository = new TradeTemplateRepository(store);
        List<TradeTemplate> templates = Arrays.asList(
                new TradeTemplate("scalp_fast", "快进快出", 0.20d, 35d, 70d, "market"),
                new TradeTemplate("swing_basic", "标准波段", 0.10d, 120d, 240d, "both")
        );

        repository.saveTemplates(templates, "swing_basic", "scalp_fast");

        assertEquals("swing_basic", repository.getDefaultTemplate().getTemplateId());
        assertEquals("scalp_fast", repository.getQuickTradeTemplate().getTemplateId());
        assertEquals("swing_basic", store.tradeDefaultTemplateId);
        assertEquals("scalp_fast", store.tradeQuickTemplateId);
        assertFalse(store.tradeTemplatesJson.isEmpty());
    }

    @Test
    public void applyTemplateShouldWriteTemplateMetadataIntoTradeCommand() {
        FakeStore store = new FakeStore();
        TradeTemplateRepository repository = new TradeTemplateRepository(store);
        TradeTemplate template = new TradeTemplate("scalp_fast", "快进快出", 0.20d, 35d, 70d, "market");
        TradeCommand command = TradeCommandFactory.openMarket(
                "acc-1",
                "BTCUSD",
                "buy",
                0.20d,
                65000d,
                0d,
                0d
        );

        TradeCommand applied = repository.applyTemplate(command, template);

        assertEquals("scalp_fast", applied.getParams().optString("templateId", ""));
        assertEquals("快进快出", applied.getParams().optString("templateName", ""));
        assertEquals("OPEN_MARKET", applied.getAction());
    }

    private static final class FakeStore implements TradeTemplateRepository.Store {
        private double tradeDefaultVolume;
        private double tradeDefaultSl;
        private double tradeDefaultTp;
        private String tradeDefaultTemplateId = "";
        private String tradeQuickTemplateId = "";
        private String tradeTemplatesJson = "";

        @Override
        public double getTradeDefaultVolume() {
            return tradeDefaultVolume;
        }

        @Override
        public void setTradeDefaultVolume(double volume) {
            tradeDefaultVolume = volume;
        }

        @Override
        public double getTradeDefaultSl() {
            return tradeDefaultSl;
        }

        @Override
        public void setTradeDefaultSl(double sl) {
            tradeDefaultSl = sl;
        }

        @Override
        public double getTradeDefaultTp() {
            return tradeDefaultTp;
        }

        @Override
        public void setTradeDefaultTp(double tp) {
            tradeDefaultTp = tp;
        }

        @Override
        public String getTradeDefaultTemplateId() {
            return tradeDefaultTemplateId;
        }

        @Override
        public void setTradeDefaultTemplateId(String templateId) {
            tradeDefaultTemplateId = templateId == null ? "" : templateId;
        }

        @Override
        public String getTradeQuickTemplateId() {
            return tradeQuickTemplateId;
        }

        @Override
        public void setTradeQuickTemplateId(String templateId) {
            tradeQuickTemplateId = templateId == null ? "" : templateId;
        }

        @Override
        public String getTradeTemplatesJson() {
            return tradeTemplatesJson;
        }

        @Override
        public void setTradeTemplatesJson(String templatesJson) {
            tradeTemplatesJson = templatesJson == null ? "" : templatesJson;
        }
    }
}
