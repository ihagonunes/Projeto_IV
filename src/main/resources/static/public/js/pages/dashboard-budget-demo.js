/* SPI-93: cenário de orçamento carregado apenas na demo local. */
(() => {
    const budget = 1100, spent = 640, daily = 40, remainingDays = 14;
    const projected = spent + daily * remainingDays;
    const money = value => value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
    window.AtlasBudgetDemo = {
        budget, spent, daily, remainingDays, projected, money,
        applies: item => item?.orcamento == null && item?.custoReal != null && Number(item.custoReal) === 0
    };
})();
