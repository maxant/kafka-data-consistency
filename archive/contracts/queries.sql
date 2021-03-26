select *
from CONTRACTS c
join PRODUCTS p on p.CONTRACT_ID = c.ID
join BUILDING_PRODUCTS h on h.ID = p.ID
where c.CONTRACTNUMBER = '1568556465087'
-- and c.FROM_ <= '2019-09-01' and '2019-09-01' <= c.TO_
order by p.FROM_
;

desc CONTRACTS;